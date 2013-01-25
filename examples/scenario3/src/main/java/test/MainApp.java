package test;

import org.apache.lucene.search.Query;
import org.hibernate.search.query.dsl.QueryBuilder;
import org.hibernate.search.query.engine.spi.EntityInfo;
import org.hibernate.search.query.engine.spi.HSQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pt.ist.fenixframework.Atomic;
import pt.ist.fenixframework.DomainRoot;
import pt.ist.fenixframework.FenixFramework;
import pt.ist.fenixframework.hibernatesearch.HibernateSearchSupport;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class MainApp {

    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);

    public static final int AUTH_COUNT = 10;
    public static final int PUB_COUNT = 20;
    public static final int BOOK_COUNT = 50;
    public static final int SLEEP_TIME = 10000;
    public static final String NAME_SPLITTER = "_";
    private static String nodeName;

    public static void main(String[] args) {
        if (args.length < 2) {
            error("Expected two arguments: <hostname> and <number of nodes expected>");
            System.exit(1);
        }

        nodeName = args[0];
        int expectedNodes = Integer.parseInt(args[1]);

        try {
            initDomain();
            sleepAWhile();
            doQueries();
            modifyDomain();
            sleepAWhile();
            doMoreQueries();
            FenixFramework.barrier("finish", expectedNodes);
        } catch (InterruptedException e) {
            logger.error("Interrupted!");
        } catch (Exception e) {
            logger.error("Error", e);
        } finally {
            FenixFramework.shutdown();
        }
    }

    @Atomic
    public static void populateAuthors() {
        DomainRoot domainRoot = FenixFramework.getDomainRoot();

        // Authors
        debug("Populate " + AUTH_COUNT + " authors");
        for (int i = 0; i < AUTH_COUNT; i++) {
            domainRoot.addTheAuthors(new Author(authorName(i)));
        }
    }

    @Atomic
    public static void populatedPublishers() {
        DomainRoot domainRoot = FenixFramework.getDomainRoot();

        // Publishers
        debug("Populate " + PUB_COUNT + " publishers");
        for (int i = 0; i < PUB_COUNT; i++) {
            domainRoot.addThePublishers(new Publisher(publisherName(i)));
        }
    }

    @Atomic
    public static void populateBooks() {
        DomainRoot domainRoot = FenixFramework.getDomainRoot();

        // Books
        debug("Populate " + BOOK_COUNT + " books");
        for (int i = 0; i < BOOK_COUNT; i++) {
            Book book = null;

            switch (i%3) {
                case 0:
                    book = new Book(bookName(i));
                    break;
                case 1:
                    book = new ComicBook(bookName(i));
                    break;
                case 2:
                    book = new ScifiBook(bookName(i));
                    break;
            }

            domainRoot.addTheBooks(book);
        }
    }

    public static void initDomain() {
        info("Populate domain");

        populateAuthors();

        populatedPublishers();

        populateBooks();

        info("Populate domain finished");
    }

    @Atomic
    public static void doQueries() {
        debug("Doing example queries. Configured " + AUTH_COUNT + " authors, " + PUB_COUNT
                + " publishers, and " + BOOK_COUNT + " books");

        debug("Find " + bookName(BOOK_COUNT / 2) + ": " + performQuery(Book.class, "bookName", bookName(BOOK_COUNT / 2)));

        debug("Find Book*1: " + performWildcardQuery(Book.class, "bookName", "book*1"));

        debug("Find ScifiBook*: " + performWildcardQuery(ScifiBook.class, "bookName", "book*"));

        debug("Find Scifi Books by " + authorName(0) + ": " + performQuery(ScifiBook.class, "authors.id",
                getAuthorByName(authorName(0)).getExternalId()));
    }

    @Atomic
    public static void modifyDomain() {
        debug("Adding books to " + authorName(0));

        Author auth0 = getAuthorByName(authorName(0));
        for (int i = 0; i < 20; i++) {
            auth0.addBooks(getBookByName(bookName(i)));
        }
    }

    @Atomic
    public static void doMoreQueries() {
        debug("Find Scifi Books by " + authorName(0) + ": " + performQuery(ScifiBook.class, "authors.id",
                getAuthorByName(authorName(0)).getExternalId()));
    }

    // See
    // https://docs.jboss.org/hibernate/search/4.2/reference/en-US/html_single/#section-building-lucene-queries
    // for more examples on how to build queries

    @SuppressWarnings("unchecked")
    public static <T> Collection<T> performQuery(Class<T> cls, String field, String queryString) {
        ArrayList<T> matchingObjects = new ArrayList<T>();

        QueryBuilder qb = HibernateSearchSupport.getSearchFactory().buildQueryBuilder().forEntity(cls).get();
        Query query = qb.keyword().onField(field).matching(queryString).createQuery();
        HSQuery hsQuery = HibernateSearchSupport.getSearchFactory().createHSQuery().luceneQuery(query)
                .targetedEntities(Arrays.<Class<?>>asList(cls));
        hsQuery.getTimeoutManager().start();
        for (EntityInfo ei : hsQuery.queryEntityInfos()) {
            matchingObjects.add((T) FenixFramework.getDomainObject((String) ei.getId()));
        }
        hsQuery.getTimeoutManager().stop();

        return matchingObjects;
    }

    @SuppressWarnings("unchecked")
    public static <T> Collection<T> performWildcardQuery(Class<T> cls, String field, String queryString) {
        ArrayList<T> matchingObjects = new ArrayList<T>();

        QueryBuilder qb = HibernateSearchSupport.getSearchFactory().buildQueryBuilder().forEntity(cls).get();
        Query query = qb.keyword().wildcard().onField(field).matching(queryString).createQuery();
        HSQuery hsQuery = HibernateSearchSupport.getSearchFactory().createHSQuery().luceneQuery(query)
                .targetedEntities(Arrays.<Class<?>>asList(cls));
        hsQuery.getTimeoutManager().start();
        for (EntityInfo ei : hsQuery.queryEntityInfos()) {
            matchingObjects.add((T) FenixFramework.getDomainObject((String) ei.getId()));
        }
        hsQuery.getTimeoutManager().stop();

        return matchingObjects;
    }

    @Atomic
    public static Author getAuthorByName(String authorName) {
        DomainRoot domainRoot = FenixFramework.getDomainRoot();
        for (Author author : domainRoot.getTheAuthors()) {
            if (author.getName().equals(authorName)) {
                return author;
            }
        }
        return null;
    }

    @Atomic
    public static Book getBookByName(String bookName) {
        DomainRoot domainRoot = FenixFramework.getDomainRoot();
        for (Book book : domainRoot.getTheBooks()) {
            if (book.getBookName().equals(bookName)) {
                return book;
            }
        }
        return null;
    }

    public static void  sleepAWhile() {
        try {
            debug("Sleeping for " + SLEEP_TIME + " milliseconds");
            Thread.sleep(SLEEP_TIME);
        } catch (InterruptedException e) {
            //no-op
        }
    }

    private static String authorName(int id) {
        return "Author" + NAME_SPLITTER + nodeName + NAME_SPLITTER + id;
    }

    private static String bookName(int id) {
        return "Book" + NAME_SPLITTER + nodeName + NAME_SPLITTER + id;
    }

    private static String publisherName(int id) {
        return "Publisher" + NAME_SPLITTER + nodeName + NAME_SPLITTER + id;
    }

    private static void debug(String message) {
        logger.debug("[" + nodeName + "] " + message);
    }

    private static void info(String message) {
        logger.info("[" + nodeName + "] " + message);
    }

    private static void error(String message) {
        logger.error("[" + nodeName + "] " + message);
    }
}
