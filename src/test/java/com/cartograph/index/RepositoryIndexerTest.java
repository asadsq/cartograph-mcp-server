/*
 * RepositoryIndexerTest.java
 * --------------------------
 * Purpose (plain English): Checks the whole job end to end — point Cartograph at a small
 * folder of Java files and confirm it comes back with the right map, skips build output, and
 * honours the caller's exclusions.
 */
package com.cartograph.index;

import com.cartograph.graph.Neighbour;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryIndexerTest {

    @TempDir
    Path repo;

    private final RepositoryIndexer indexer = new RepositoryIndexer();

    private void write(String relativePath, String source) throws IOException {
        Path file = repo.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, source);
    }

    /** A tiny app: Service uses Repository, and both live under src/main/java. */
    @BeforeEach
    void buildSampleRepo() throws IOException {
        write("src/main/java/com/app/Service.java", """
                package com.app;

                import com.app.data.Repository;

                public class Service {
                    private final Repository repository = new Repository();
                }
                """);
        write("src/main/java/com/app/data/Repository.java", """
                package com.app.data;

                public class Repository {
                }
                """);
    }

    @Test
    void mapsASmallRepository() throws IOException {
        IndexedRepository indexed = indexer.index(repo, List.of(), List.of());

        assertEquals(2, indexed.filesParsed());
        assertEquals(2, indexed.graph().typeCount());
        assertEquals(1, indexed.graph().dependencyCount());
        assertEquals(List.of("java"), indexed.languages());

        List<Neighbour> dependencies = indexed.graph().dependencies("com.app.Service", 1);
        assertEquals(List.of("com.app.data.Repository"), dependencies.stream().map(Neighbour::id).toList());

        List<Neighbour> dependents = indexed.graph().dependents("com.app.data.Repository", 1);
        assertEquals(List.of("com.app.Service"), dependents.stream().map(Neighbour::id).toList());
    }

    @Test
    void skipsBuildOutputSoGeneratedCodeDoesNotPolluteTheMap() throws IOException {
        write("target/classes/com/app/Generated.java", """
                package com.app;
                public class Generated { }
                """);

        IndexedRepository indexed = indexer.index(repo, List.of(), List.of());

        assertEquals(2, indexed.filesParsed(), "files under target/ should not be read");
        assertFalse(indexed.graph().contains("com.app.Generated"));
    }

    @Test
    void honoursCallerSuppliedExclusions() throws IOException {
        IndexedRepository indexed = indexer.index(repo, List.of(), List.of("**/data/**"));

        assertEquals(1, indexed.filesParsed());
        assertFalse(indexed.graph().contains("com.app.data.Repository"));
    }

    @Test
    void reportsHowMuchOfTheCodeItFollowed() throws IOException {
        IndexedRepository indexed = indexer.index(repo, List.of(), List.of());

        assertTrue(indexed.coverage().total() > 0);
        assertEquals(indexed.coverage().total(),
                indexed.coverage().internal() + indexed.coverage().external() + indexed.coverage().unresolved(),
                "every reference must be accounted for in exactly one bucket");
    }

    @Test
    void refusesAPathThatIsNotAFolder() {
        assertThrows(IOException.class, () -> indexer.index(repo.resolve("nope"), List.of(), List.of()));
    }

    @Test
    void remembersTheMostRecentlyIndexedRepository() throws IOException {
        GraphStore store = new GraphStore();
        assertEquals(null, store.mostRecent());

        IndexedRepository indexed = indexer.index(repo, List.of(), List.of());
        store.put(indexed);

        assertEquals(indexed.path(), store.mostRecent().path());
        assertEquals(1, store.size());
    }
}
