package test;

public class Publisher extends Publisher_Base {

    public  Publisher() {
        super();
    }

    public Publisher(String publisherName) {
        this();
        setPublisherName(publisherName);
    }

    @Override
    public String toString() {
        return "Publisher " + getPublisherName();
    }
}
