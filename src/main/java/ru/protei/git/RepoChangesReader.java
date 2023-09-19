package ru.protei.git;

import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class RepoChangesReader {
    private final Repository repository;
    private boolean pullRequired = false;

    public RepoChangesReader(@NonNull String repoName, Path repositoriesRoot) throws IOException {
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
                log.info("Pulling changes from remote repository");
                git.pull().call();
                log.info("Changes pulled successfully");
            }

            List<ChangesStat> commits = getCommitsByDate(git, authors, from, to);
            log.info("Found {} commits", commits.size());
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

            // Проход по всем коммитам
            Iterable<RevCommit> logs = git.log().all().call();  // TODO: индексировать это как-то
            for (RevCommit rev : logs) {
                walk.parseBody(rev);
                long commitTime = rev.getCommitTime(); // время в формате UNIX timestamp

                // Проверка, находится ли время коммита в заданном интервале
                if (commitTime >= startTime && commitTime <= endTime
                        && authors.contains(rev.getAuthorIdent().getName())) {
                    selectedCommits.add(rev);
                }
            }

            return selectedCommits.stream()
                    .map(commit -> getCommitStat(walk, commit))
                    .collect(Collectors.toList());
        }
    }

    @SneakyThrows
    private ChangesStat getCommitStat(RevWalk revWalk, RevCommit commit) {
        List<DiffEntry> commitChanges = getCommitChanges(revWalk, commit);
        Set<String> authors = Set.of(commit.getAuthorIdent().getName());
        Set<String> changedFiles = commitChanges.stream()
                .map(DiffEntry::getNewPath)
                .collect(Collectors.toSet());
        ZonedDateTime commitDateTime = ZonedDateTime.ofInstant(
                commit.getAuthorIdent().getWhen().toInstant(), ZoneId.systemDefault());
        return new ChangesStatData(commitDateTime, changedFiles.size(), changedFiles, authors);
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
