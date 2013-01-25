package test;

public class Author extends Author_Base {

    public  Author() {
        super();
    }

    public Author(String name) {
        this();
        setName(name);
    }

    @Override
    public String toString() {
        return "Author " + getName();
    }

}
