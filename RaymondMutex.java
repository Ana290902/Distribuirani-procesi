import java.util.*;
 
/*
 * Raymondov token-based algoritam nad razapinjucim stablom.
 * Stablo (susjedi) dolazi iz vec postojeceg mehanizma Topology.java / topology<id>
 * datoteka - ovdje se ne dira nista u infrastrukturi.
 *
 * HOLDER pokazuje prema privilegiranom cvoru (ili je SELF ako smo mi trenutno
 * privilegirani). Algoritam ne koristi vremenske oznake.
 */
public class RaymondMutex extends Process implements Lock {
 
    private static final int SELF = -1; // oznaka "self" u HOLDER i REQUEST_Q
 
    private int holder;                 // SELF ili id susjeda prema privilegiji
    private boolean using = false;
    private boolean asked = false;
    private final LinkedList<Integer> requestQ = new LinkedList<>();
 
    /**
     * @param initialParent id susjeda prema kojem HOLDER pocetno pokazuje
     *                       (put prema korijenu stabla), ili SELF (-1) ako je
     *                       ovaj proces pocetni vlasnik privilegije (korijen)
     */
    public RaymondMutex(Linker initComm, int initialParent) {
        super(initComm);
        holder = initialParent;
    }
 
    @Override
    public synchronized void requestCS() {
        requestQ.add(SELF);
        assignPrivilege();
        makeRequest();
        while (!using) myWait();
    }
 
    @Override
    public synchronized void releaseCS() {
        using = false;
        assignPrivilege();
        makeRequest();
    }
 
    @Override
    public synchronized void handleMsg(Msg m, int src, String tag) {
        if (tag.equals("ray_request")) {
            requestQ.add(src);
            assignPrivilege();
            makeRequest();
        } else if (tag.equals("ray_privilege")) {
            holder = SELF;
            assignPrivilege();
            makeRequest();
        }
    }
 
    /** Ako smo privilegirani, slobodni i red nije prazan - proslijedi privilegiju dalje. */
    private synchronized void assignPrivilege() {
        if (holder == SELF && !using && !requestQ.isEmpty()) {
            int j = requestQ.removeFirst();
            if (j == SELF) {
                using = true;   // ulazak u kriticnu sekciju - budi requestCS()
                notify();
            } else {
                holder = j;
                asked = false;
                sendMsg(j, "ray_privilege");
            }
        }
    }
 
    /** Ako nesto cekamo, a jos nismo zatrazili privilegiju od trenutnog HOLDER-a, zatrazi je. */
    private synchronized void makeRequest() {
        if (!requestQ.isEmpty() && holder != SELF && !asked) {
            asked = true;
            sendMsg(holder, "ray_request");
        }
    }
}
 