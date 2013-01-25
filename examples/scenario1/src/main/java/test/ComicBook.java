package test;

public class ComicBook extends ComicBook_Base {

    public  ComicBook() {
        super();
    }

    public ComicBook(String name) {
        this();
        setBookName(name);
    }

    @Override
    public String toString() {
        return super.toString() + " (Comic)";
    }

}
