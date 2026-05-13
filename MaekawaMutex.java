import java.util.*;

/*
 * Maekawin quorum-based algoritam za distribuirano medjusobno iskljucivanje.
 * Koristi REQUEST, REPLY, RELEASE te dodatne FAILED, INQUIRE i YIELD poruke
 * za razrjesavanje moguceg zastoja.
 */
public class MaekawaMutex extends Process implements Lock {
    LamportClock c = new LamportClock();

    private IntLinkedList quorum = new IntLinkedList();
    private boolean[] replyReceived;
    private int replyCount = 0;

    private boolean requesting = false;
    private boolean executing = false;
    private boolean failedReceived = false;
    private int pendingInquireFrom = -1;

    private int myts = Symbols.Infinity;

    /* Cvor kao arbitar moze biti zakljucan samo za jedan zahtjev. */
    private int lockedFor = -1;
    private int lockedTs = Symbols.Infinity;

    /* Red cekanja zahtjeva za koje ovaj proces kao arbitar jos nije dao dopustenje. */
    private PriorityQueue<Request> waitingQ = new PriorityQueue<Request>();

    public MaekawaMutex(Linker initComm) {
        super(initComm);
        replyReceived = new boolean[N];
        buildQuorum();
    }

    public synchronized void requestCS() {
        requesting = true;
        executing = false;
        failedReceived = false;
        pendingInquireFrom = -1;
        replyCount = 0;
        for (int i = 0; i < N; i++) replyReceived[i] = false;

        c.tick();
        myts = c.getValue();

        /* Vlastiti proces je clan vlastitog kvoruma. Obradjujemo ga lokalno,
           kao da je primljena vlastita REQUEST poruka. */
        handleLocalRequest(myId, myts);

        for (int i = 0; i < quorum.size(); i++) {
            int q = quorum.getEntry(i);
            if (q != myId) sendMsg(q, "maekawa_request", myts, myId);
        }

        while (!okayCS()) myWait();
        requesting = false;
        executing = true;
    }

    public synchronized void releaseCS() {
        executing = false;
        myts = Symbols.Infinity;

        for (int i = 0; i < quorum.size(); i++) {
            int q = quorum.getEntry(i);
            if (q == myId) handleLocalRelease(myId);
            else sendMsg(q, "maekawa_release", c.getValue(), myId);
        }
    }

    private boolean okayCS() {
        return replyCount == quorum.size();
    }

    public synchronized void handleMsg(Msg m, int src, String tag) {
        StringTokenizer st = new StringTokenizer(m.getMessage());

        if (tag.equals("maekawa_request")) {
            int ts = Integer.parseInt(st.nextToken());
            int pid = Integer.parseInt(st.nextToken());
            c.receiveAction(src, ts);
            handleRequest(pid, ts);
        } else if (tag.equals("maekawa_reply")) {
            int ts = Integer.parseInt(st.nextToken());
            c.receiveAction(src, ts);
            if (!replyReceived[src]) {
                replyReceived[src] = true;
                replyCount++;
            }
            if (okayCS()) notifyAll();
        } else if (tag.equals("maekawa_release")) {
            int ts = Integer.parseInt(st.nextToken());
            int pid = Integer.parseInt(st.nextToken());
            c.receiveAction(src, ts);
            handleRelease(pid);
        } else if (tag.equals("maekawa_failed")) {
            failedReceived = true;
            if (pendingInquireFrom != -1) {
                int tmp = pendingInquireFrom;
                pendingInquireFrom = -1;
                handleInquire(tmp);
            }
        } else if (tag.equals("maekawa_inquire")) {
            int ts = Integer.parseInt(st.nextToken());
            c.receiveAction(src, ts);
            handleInquire(src);
        } else if (tag.equals("maekawa_yield")) {
            int ts = Integer.parseInt(st.nextToken());
            int pid = Integer.parseInt(st.nextToken());
            c.receiveAction(src, ts);
            handleYield(pid);
        }
    }

    private void handleLocalRequest(int pid, int ts) {
        handleRequest(pid, ts);
    }

    private void handleRequest(int pid, int ts) {
        Request r = new Request(pid, ts);

        if (lockedFor == -1) {
            grant(r);
            return;
        }

        waitingQ.add(r);

        Request current = new Request(lockedFor, lockedTs);
        Request bestWaiting = waitingQ.peek();

        /* Ako neki vec postojeci zahtjev ima prednost pred novim zahtjevom,
           novi zahtjev dobiva FAILED. Inace se pokusava povuci ranije dano dopustenje. */
        if (current.compareTo(r) < 0 || (bestWaiting != null && bestWaiting.compareTo(r) < 0)) {
            if (pid != myId) sendMsg(pid, "maekawa_failed", c.getValue(), myId);
            else failedReceived = true;
        } else {
            if (lockedFor != myId) sendMsg(lockedFor, "maekawa_inquire", c.getValue(), myId);
            else handleInquire(myId);
        }
    }

    private void handleInquire(int from) {
        /* Ako proces jos ne zna hoce li uspjeti prikupiti sva dopustenja,
           odgovor na INQUIRE se odgadja. Cim primi FAILED, vratit ce dopustenje. */
        if (requesting && !executing && failedReceived && !okayCS()) {
            if (replyReceived[from]) {
                replyReceived[from] = false;
                replyCount--;
            }
            if (from != myId) sendMsg(from, "maekawa_yield", c.getValue(), myId);
            else handleYield(myId);
        } else if (requesting && !executing && !okayCS()) {
            pendingInquireFrom = from;
        }
    }

    private void handleYield(int pid) {
        if (lockedFor != -1) {
            waitingQ.add(new Request(lockedFor, lockedTs));
        }
        lockedFor = -1;
        lockedTs = Symbols.Infinity;
        grantNextFromQueue();
    }

    private void handleLocalRelease(int pid) {
        handleRelease(pid);
    }

    private void handleRelease(int pid) {
        if (lockedFor == pid) {
            lockedFor = -1;
            lockedTs = Symbols.Infinity;
            grantNextFromQueue();
        } else {
            removeFromQueue(pid);
        }
    }

    private void grantNextFromQueue() {
        if (!waitingQ.isEmpty()) {
            Request next = waitingQ.poll();
            grant(next);
        }
    }

    private void grant(Request r) {
        lockedFor = r.pid;
        lockedTs = r.ts;

        if (r.pid == myId) {
            if (!replyReceived[myId]) {
                replyReceived[myId] = true;
                replyCount++;
            }
            if (okayCS()) notifyAll();
        } else {
            sendMsg(r.pid, "maekawa_reply", c.getValue(), myId);
        }
    }

    private void removeFromQueue(int pid) {
        PriorityQueue<Request> newQ = new PriorityQueue<Request>();
        while (!waitingQ.isEmpty()) {
            Request r = waitingQ.poll();
            if (r.pid != pid) newQ.add(r);
        }
        waitingQ = newQ;
    }

    private void buildQuorum() {
        if (N == 3) {
            int[][] q = { {0,1}, {1,2}, {0,2} };
            fillQuorum(q[myId]);
        } else if (N == 7) {
            int[][] q = {
                {0,1,2}, {0,3,4}, {0,5,6}, {1,3,5},
                {1,4,6}, {2,3,6}, {2,4,5}
            };
            fillQuorum(q[myId]);
        } else {
            int root = (int)Math.sqrt(N);
            if (root * root == N) {
                int row = myId / root;
                int col = myId % root;
                for (int j = 0; j < root; j++) quorum.add(row * root + j);
                for (int i = 0; i < root; i++) quorum.add(i * root + col);
            } else {
                /* Sigurna rezervna varijanta: puni kvorum. To je ispravno,
                   ali nema sqrt(N) slozenost. Za demonstraciju Maekawe koristi N=3 ili N=7. */
                for (int i = 0; i < N; i++) quorum.add(i);
            }
        }
        Util.println("Maekawa quorum for " + myId + " = " + quorum.toString());
    }

    private void fillQuorum(int[] arr) {
        for (int i = 0; i < arr.length; i++) quorum.add(arr[i]);
    }

    static class Request implements Comparable {
        int pid;
        int ts;

        Request(int pid, int ts) {
            this.pid = pid;
            this.ts = ts;
        }

        public int compareTo(Object o) {
            Request r = (Request)o;
            if (this.ts != r.ts) return this.ts - r.ts;
            return this.pid - r.pid;
        }
    }
}
