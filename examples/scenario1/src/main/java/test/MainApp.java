package test;

import java.util.*;

import pt.ist.fenixframework.Atomic;
import pt.ist.fenixframework.DomainRoot;

import pt.ist.fenixframework.FenixFramework;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainApp {

    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);

    public static final int AUTH_COUNT = 1000;
    public static final int PUB_COUNT = 50;
    public static final int BOOK_COUNT = 1000;

    public static void main(String[] args) {
        try {
            initDomain();
        } finally {
            FenixFramework.shutdown();
        }
    }

    @Atomic
    public static void initDomain() {
        logger.info("Populate domain");
        DomainRoot domainRoot = FenixFramework.getDomainRoot();

        // Authors
        logger.debug("Populate " + AUTH_COUNT + " authors");
        for (int i = 0; i < AUTH_COUNT; i++) {
            domainRoot.addTheAuthors(new Author("Auth" + i));
        }

        // Publishers
        logger.debug("Populate " + PUB_COUNT + " publishers");
        for (int i = 0; i < PUB_COUNT; i++) {
            domainRoot.addThePublishers(new Publisher("Pub" + i));
        }

        // Books
        logger.debug("Populate " + BOOK_COUNT + " books");
        for (int i = 0; i < BOOK_COUNT; i++) {
            Book book = null;

            switch (i%3) {
                case 0:
                    book = new Book("Book" + i);
                    break;
                case 1:
                    book = new ComicBook("Book" + i);
                    break;
                case 2:
                    book = new ScifiBook("Book" + i);
                    break;
            }

            domainRoot.addTheBooks(book);
        }
        logger.info("Populate domain finished");
    }

}
