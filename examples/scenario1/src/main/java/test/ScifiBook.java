package test;

public class ScifiBook extends ScifiBook_Base {

    public  ScifiBook() {
        super();
    }

    public ScifiBook(String name) {
        this();
        setBookName(name);
    }

    @Override
    public String toString() {
        return super.toString() + " (Scifi)";
    }

}
