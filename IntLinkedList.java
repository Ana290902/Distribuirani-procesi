import java.util.LinkedList;

public class IntLinkedList extends LinkedList {

    public boolean addInt(int i) {
        return super.add(Integer.valueOf(i));
    }

    public boolean containsInt(int i) {
        return super.contains(Integer.valueOf(i));
    }

    public int getEntry(int index) {
        return ((Integer) super.get(index)).intValue();
    }

    public boolean removeObject(int i) {
        return super.remove(Integer.valueOf(i));
    }
}