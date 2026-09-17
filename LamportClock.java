public class LamportClock {
    int c;
    public LamportClock() {
        c = 0;  // izmijenjeno sa 1 na 0 prema izvoru Distributed Computing: Principles, Algorithms and Systems}, Cambridge University Press, 2008.
    }
    public int getValue() {
        return c;
    }
    public void tick() { // on internal events
        c = c + 1;
    }
    public void sendAction() {
       // include c in message
        c = c + 1;      
    }
    public void receiveAction(int src, int sentValue) {
        c = Util.max(c, sentValue) + 1;
    }
}
