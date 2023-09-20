package ru.protei;

import lombok.RequiredArgsConstructor;
import org.eclipse.jgit.api.errors.GitAPIException;
import ru.protei.analytics.ActivityAnalyzer;
import ru.protei.analytics.CombinatedChangesStat;
import ru.protei.config.Config;
import ru.protei.git.RepoChangesReader;

import java.io.IOException;

@RequiredArgsConstructor
public class WorkCounter {
    private final Config config;

    public static void main(String[] args) throws Exception {
        var workCounter = new WorkCounter(new Config("/config.properties"));
        workCounter.analyze(workCounter.readStat());
    }

    private CombinatedChangesStat readStat() throws GitAPIException, IOException {
        CombinatedChangesStat result = null;
        for (var repo : config.getRepoNames()) {
            var reader = new RepoChangesReader(repo, config.getRepositoriesRoot());
            reader.setPullRequired(config.isPullRequired());
            var changes = reader.readChanges(config.getAuthors(), config.getFrom(), config.getTo());
            if (result == null) {
                result = changes;
            } else {
                result = result.combine(changes);
            }
        }
        return result;
    }

    private void analyze(CombinatedChangesStat changes) {
        var analyzer = new ActivityAnalyzer(changes);
        analyzer.linearAnalyze();
    }
}
