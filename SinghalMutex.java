import java.util.*;

/*
 * Singhalov dinamicki algoritam za distribuirano medjusobno iskljucivanje.
 * Implementacija koristi istu programsku infrastrukturu kao kodovi iz skripte:
 * Process, Lock, Linker, Msg, ListenerThread i LamportClock.
 */
public class SinghalMutex extends Process implements Lock {
    LamportClock c = new LamportClock();

    private boolean requesting = false;
    private boolean executing = false;
    private int myts = Symbols.Infinity;

    /* R_i: procesi od kojih moram dobiti dopustenje. Vlastiti proces se ne sprema
       jer je vlastito dopustenje implicitno. */
    private TreeSet<Integer> requestSet = new TreeSet<Integer>();

    /* I_i: procesi kojima dugujem REPLY nakon izlaska iz kriticne sekcije. */
    private TreeSet<Integer> informSet = new TreeSet<Integer>();

    public SinghalMutex(Linker initComm) {
        super(initComm);

        /* Inicijalizacija prema staircase obrascu iz algoritma.
           U knjizi je R_i = {S_1, ..., S_i}; ovdje su identifikatori 0..N-1,
           a vlastito dopustenje je implicitno, pa spremamo samo 0..myId-1. */
        for (int j = 0; j < myId; j++) requestSet.add(j);
    }

    public synchronized void requestCS() {
        requesting = true;
        c.tick();
        myts = c.getValue();

        Integer[] targets = requestSet.toArray(new Integer[0]);
        for (int k = 0; k < targets.length; k++) {
            sendMsg(targets[k].intValue(), "singhal_request", myts, myId);
        }

        while (!requestSet.isEmpty()) myWait();

        requesting = false;
        executing = true;
    }

    public synchronized void releaseCS() {
        executing = false;
        myts = Symbols.Infinity;

        Integer[] targets = informSet.toArray(new Integer[0]);
        for (int k = 0; k < targets.length; k++) {
            int pid = targets[k].intValue();
            informSet.remove(pid);
            sendMsg(pid, "singhal_reply", c.getValue(), myId);
            if (pid != myId) requestSet.add(pid);
        }
    }

    private boolean myRequestHasPriority(int otherTs, int otherId) {
        if (myts == Symbols.Infinity) return false;
        return (myts < otherTs) || ((myts == otherTs) && (myId < otherId));
    }

    public synchronized void handleMsg(Msg m, int src, String tag) {
        StringTokenizer st = new StringTokenizer(m.getMessage());

        if (tag.equals("singhal_request")) {
            int ts = Integer.parseInt(st.nextToken());
            int sender = Integer.parseInt(st.nextToken());
            c.receiveAction(src, ts);

            if (requesting) {
                if (myRequestHasPriority(ts, sender)) {
                    informSet.add(sender);
                } else {
                    sendMsg(sender, "singhal_reply", c.getValue(), myId);
                    if (!requestSet.contains(sender)) {
                        requestSet.add(sender);
                        sendMsg(sender, "singhal_request", myts, myId);
                    }
                }
            } else if (executing) {
                informSet.add(sender);
            } else {
                if (sender != myId) requestSet.add(sender);
                sendMsg(sender, "singhal_reply", c.getValue(), myId);
            }
        } else if (tag.equals("singhal_reply")) {
            int ts = Integer.parseInt(st.nextToken());
            c.receiveAction(src, ts);
            requestSet.remove(src);
            if (requestSet.isEmpty()) notifyAll();
        }
    }
}
