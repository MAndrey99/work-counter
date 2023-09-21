package ru.protei.git;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.*;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import ru.protei.analytics.ChangesStat;
import ru.protei.analytics.CombinatedChangesStat;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class RepoChangesReader {
    private final Repository repository;
    private final String repoName;
    private boolean pullRequired = false;

    public RepoChangesReader(@NonNull String repoName, Path repositoriesRoot) throws IOException {
        this.repoName = repoName;
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        var repoPath = repositoriesRoot.resolve(repoName);
        var repoDir = repoPath.toFile();
        if (!repoDir.isDirectory()) {
            throw new IOException(String.format("Repository %s not found", repoName));
        }
        repository = builder.setGitDir(repoPath.resolve(".git").toFile())
                .readEnvironment() // scan environment GIT_* variables
                .findGitDir() // scan up the file system tree
                .build();
    }

    public void setPullRequired(boolean pullRequired) {
        this.pullRequired = pullRequired;
    }

    public CombinatedChangesStat readChanges(@NonNull Collection<String> authors,
                                             @NonNull ZonedDateTime from,
                                             ZonedDateTime to) throws GitAPIException, IOException {
        try (Git git = new Git(repository)) {
            if (pullRequired) {
                log.info("Pulling changes from remote repository {}...", repoName);
                git.pull().call();
                log.info("Changes pulled successfully");
            }

            List<ChangesStat> commits = getCommitsByDate(git, authors, from, to);
            log.info("Found {} commits in repository {}", commits.size(), repoName);
            log.debug("Commits: {}", commits);
            return new CombinatedChangesStat(commits);
        }
    }

    private List<ChangesStat> getCommitsByDate(
            @NonNull Git git, @NonNull Collection<String> authors,
            @NonNull ZonedDateTime from, ZonedDateTime to
    ) throws IOException, GitAPIException {
        List<RevCommit> selectedCommits = new ArrayList<>();

        try (RevWalk walk = new RevWalk(repository)) {
            // Конвертация ZonedDateTime в UNIX timestamp
            long startTime = from.toEpochSecond();
            long endTime = (to == null) ? Long.MAX_VALUE : to.toEpochSecond();

            // Проход по всем коммитам TODO: ориентироваться бы на какой-то интервал
            Iterable<RevCommit> logs = git.log().all().call();
            for (RevCommit rev : logs) {
                walk.parseBody(rev);
                long commitTime = rev.getCommitTime(); // время в формате UNIX timestamp

                // Проверка, находится ли время коммита в заданном интервале
                if (commitTime >= startTime && commitTime <= endTime
                        && authors.contains(rev.getAuthorIdent().getName())) {
                    selectedCommits.add(rev);
                }
            }

            var result = selectedCommits.stream()
                    .map(commit -> getCommitStat(walk, commit))
                    .collect(Collectors.toList());
            fillBranches(result);
            return new ArrayList<>(result);
        }
    }

    @SneakyThrows
    private CommitChangesStat getCommitStat(RevWalk revWalk, RevCommit commit) {
        List<DiffEntry> commitChanges = getCommitChanges(revWalk, commit);
        Set<String> authors = Set.of(commit.getAuthorIdent().getName());
        Set<String> changedFiles = commitChanges.stream()
                .map(DiffEntry::getNewPath)
                .collect(Collectors.toSet());
        ZonedDateTime commitDateTime = ZonedDateTime.ofInstant(
                commit.getAuthorIdent().getWhen().toInstant(), ZoneId.systemDefault());

        int changesCount = 0;
        DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
        diffFormatter.setRepository(repository);
        for (DiffEntry diffEntry : commitChanges) {
            EditList editList = diffFormatter.toFileHeader(diffEntry).toEditList();
            for (Edit edit : editList) {
                changesCount += edit.getLengthA(); // Удаленные строки
                changesCount += edit.getLengthB(); // Добавленные строки
                // TODO: а при перемещении файла проблем не возникнет?
            }
        }

        return new CommitChangesStat(commitDateTime, changesCount,
                changedFiles, authors, new HashSet<>(), commit.getId());
    }

    public void fillBranches(List<CommitChangesStat> commitChangesStatList) throws IOException, GitAPIException {
        // TODO: не всегда определяется ветка
        Map<ObjectId, CommitChangesStat> commitIdToChangesStatData = commitChangesStatList.stream()
                .collect(Collectors.toMap(CommitChangesStat::getCommitId, Function.identity()));
        // тут собираем все коммиты являющиеся головами веток
        Map<ObjectId, List<String>> heads = repository.getRefDatabase().getRefsByPrefix("refs/heads/").stream()
                .map(ref -> Map.entry(ref.getObjectId(), ref.getName().replace("refs/heads/", "")))
                .collect(Collectors.toMap(Map.Entry::getKey, e -> Collections.singletonList(e.getValue()), (a, b) -> {
                    List<String> result = new ArrayList<>(a.size() + b.size());
                    result.addAll(a);
                    result.addAll(b);
                    return result;
                }));

        // заполняем коммиты хранящиеся в каждой ветке
        Map<String, Set<ObjectId>> branchToCommits = new HashMap<>();
        try (Git git = new Git(repository)) {
            List<Ref> branches = git.branchList().call();
            log.info("Found {} branches in repository {}: {}", branches.size(), repoName,
                    branches.stream().map(Ref::getName).collect(Collectors.toList()));
            for (Ref branch : branches) {
                RevCommit latestCommitOnBranch = repository.parseCommit(branch.getObjectId());
                Iterable<RevCommit> commits = git.log().add(latestCommitOnBranch).call();
                for (RevCommit c : commits) {
                    // TODO: тут тоже бы не проходить все коммиты, а ориентироваться
                    //  на какой-то интервал или пограничные коммиты
                    CommitChangesStat commitChangesStat = commitIdToChangesStatData.get(c.getId());
                    if (commitChangesStat != null) {
                        String shortName = branch.getName().replace("refs/heads/", "");
                        branchToCommits.computeIfAbsent(shortName, k -> new HashSet<>()).add(c.getId());
                    }
                }
            }
        }

        // если ветка содержит коммит из heads, значит далее надо игнорировать все коммиты той ветки в рамках более общей
        removeInnerBranches(branchToCommits, heads);

        // непосредственно заполнение веток в commitIdToChangesStatData
        for (var entry : branchToCommits.entrySet()) {
            String branchName = entry.getKey();
            Set<ObjectId> commits = entry.getValue();
            for (ObjectId commitId : commits) {
                CommitChangesStat commitChangesStat = commitIdToChangesStatData.get(commitId);
                if (commitChangesStat != null) {
                    commitChangesStat.getBranches().add(branchName);
                }
            }
        }

        for (CommitChangesStat commitChangesStat : commitChangesStatList) {
            log.warn("Commit {} has no branches ({})", commitChangesStat.getCommitId(), repoName);
        }
    }

    private void removeInnerBranches(Map<String, Set<ObjectId>> branchToCommits, Map<ObjectId, List<String>> heads) {
        for (var entry : branchToCommits.entrySet()) {
            String branchName = entry.getKey();
            Set<ObjectId> commits = entry.getValue();
            Set<ObjectId> updatedCommits = new HashSet<>(commits);
            for (ObjectId commitId : commits) {
                var branches = heads.getOrDefault(commitId, Collections.emptyList());
                if (!branches.contains(branchName)) {
                    for (var b : branches) {
                        // удаляем все коммиты ветки {b} из {branchName}
                        updatedCommits.removeAll(branchToCommits.getOrDefault(b, Collections.emptySet()));
                    }
                }
            }
            branchToCommits.put(branchName, updatedCommits);
        }
    }

    private List<DiffEntry> getCommitChanges(RevWalk walk, RevCommit commit) throws IOException {
        RevCommit parent = null;
        if (commit.getParentCount() > 0) {
            parent = walk.parseCommit(commit.getParent(0).getId());
        }

        if (parent == null) {
            // Initial commit has no changes since there's no parent
            return Collections.emptyList();
        }

        AbstractTreeIterator oldTreeIter = prepareTreeParser(walk, parent);
        AbstractTreeIterator newTreeIter = prepareTreeParser(walk, commit);

        DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE);
        diffFormatter.setRepository(repository);
        diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
        diffFormatter.setDetectRenames(true);

        return diffFormatter.scan(oldTreeIter, newTreeIter);
    }

    private AbstractTreeIterator prepareTreeParser(RevWalk walk, RevCommit commit) throws IOException {
        try (ObjectReader oldReader = repository.newObjectReader()) {
            RevTree tree = walk.parseTree(commit.getTree().getId());
            CanonicalTreeParser oldTreeParser = new CanonicalTreeParser();
            oldTreeParser.reset(oldReader, tree.getId());
            walk.dispose();
            return oldTreeParser;
        }
    }
}
