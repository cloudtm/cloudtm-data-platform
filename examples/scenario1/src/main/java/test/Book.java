package test;

public class Book extends Book_Base {

    public  Book() {
        super();
    }

    public Book(String name) {
        this();
        setBookName(name);
    }

    @Override
    public String toString() {
        return "Book " + getBookName();
    }

}
