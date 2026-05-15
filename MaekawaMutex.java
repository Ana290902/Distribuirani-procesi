import java.util.*;

/*
 * Maekawin quorum-based algoritam za distribuirano medjusobno iskljucivanje.
 * Koristi REQUEST, REPLY, RELEASE te dodatne FAILED, INQUIRE i YIELD poruke
 * za razrjesavanje moguceg zastoja.
 */
public class MaekawaMutex extends Process implements Lock {
    LamportClock c = new LamportClock();

    private final IntLinkedList quorum = new IntLinkedList();
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
    private boolean inquireSent = false;

    /* Red cekanja zahtjeva za koje ovaj proces kao arbitar jos nije dao dopustenje. */
    private PriorityQueue<Request> waitingQ = new PriorityQueue<>();

    public MaekawaMutex(Linker initComm) {
        super(initComm);
        replyReceived = new boolean[N];
        buildQuorum();
    }

    @Override
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

    @Override
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

    @Override
    public synchronized void handleMsg(Msg m, int src, String tag) {
        StringTokenizer st = new StringTokenizer(m.getMessage());

        switch (tag) {
            case "maekawa_request" -> {
                int ts = Integer.parseInt(st.nextToken());
                int pid = Integer.parseInt(st.nextToken());
                c.receiveAction(src, ts);
                handleRequest(pid, ts);
            }
            case "maekawa_reply" -> {
                int ts = Integer.parseInt(st.nextToken());
                c.receiveAction(src, ts);
                if (!replyReceived[src]) {
                    replyReceived[src] = true;
                    replyCount++;
                }
                if (okayCS()) notifyAll();
            }
            case "maekawa_release" -> {
                int ts = Integer.parseInt(st.nextToken());
                int pid = Integer.parseInt(st.nextToken());
                c.receiveAction(src, ts);
                handleRelease(pid);
            }
            case "maekawa_failed" -> markFailed();
            case "maekawa_inquire" -> {
                int ts = Integer.parseInt(st.nextToken());
                c.receiveAction(src, ts);
                handleInquire(src);
            }
            case "maekawa_yield" -> {
                int ts = Integer.parseInt(st.nextToken());
                int pid = Integer.parseInt(st.nextToken());
                c.receiveAction(src, ts);
                handleYield(pid);
            }
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
        if (bestWaiting != null && bestWaiting.compareTo(current) < 0) {
            if (!inquireSent) {
                inquireSent = true;

                if (lockedFor != myId) {
                    sendMsg(lockedFor, "maekawa_inquire", c.getValue(), myId);
                } else {
                    handleInquire(myId);
                }
            }

            if (bestWaiting.pid != pid) {
                if (pid != myId) {
                    sendMsg(pid, "maekawa_failed", c.getValue(), myId);
                } else {
                    markFailed();
                }
            }

        } else {
            if (pid != myId) {
                sendMsg(pid, "maekawa_failed", c.getValue(), myId);
            } else {
                markFailed();
            }
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
        if (pid != lockedFor) return;

        waitingQ.add(new Request(lockedFor, lockedTs));

        lockedFor = -1;
        lockedTs = Symbols.Infinity;
        inquireSent = false;

        grantNextFromQueue();
    }

    private void handleLocalRelease(int pid) {
        handleRelease(pid);
    }

    private void handleRelease(int pid) {
        if (lockedFor == pid) {
            lockedFor = -1;
            lockedTs = Symbols.Infinity;
            inquireSent = false;
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

    private synchronized void grant(Request r) {
        lockedFor = r.pid;
        lockedTs = r.ts;
        inquireSent = false;

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

    private void markFailed() {
        failedReceived = true;

        if (pendingInquireFrom != -1) {
            int tmp = pendingInquireFrom;
            pendingInquireFrom = -1;
            handleInquire(tmp);
        }
    }

    private void removeFromQueue(int pid) {
        PriorityQueue<Request> newQ = new PriorityQueue<>();
        while (!waitingQ.isEmpty()) {
            Request r = waitingQ.poll();
            if (r.pid != pid) newQ.add(r);
        }
        waitingQ = newQ;
    }

    private void buildQuorum() {
        switch (N) {
            case 3 -> {
                int[][] q3 = { {0,1}, {1,2}, {0,2} };
                fillQuorum(q3[myId]);
            }
            case 7 -> {
                int[][] q7 = {
                    {0, 1, 2}, // Q0
                    {1, 3, 5}, // Q1
                    {2, 3, 6}, // Q2
                    {0, 3, 4}, // Q3
                    {1, 4, 6}, // Q4
                    {2, 4, 5}, // Q5
                    {0, 5, 6}  // Q6
                };

                fillQuorum(q7[myId]);
            }
            default -> {
                int root = (int)Math.sqrt(N);
                if (root * root == N) {
                    int row = myId / root;
                    int col = myId % root;

                    for (int j = 0; j < root; j++) {
                        int pid = row * root + j;
                        if (!quorum.containsInt(pid)) quorum.addInt(pid);
                    }

                    for (int i = 0; i < root; i++) {
                        int pid = i * root + col;
                        if (!quorum.containsInt(pid)) quorum.addInt(pid);
                    }
                } else {
                    /* Sigurna rezervna varijanta: puni kvorum. To je ispravno,
                       ali nema sqrt(N) slozenost. Za demonstraciju Maekawe koristi N=3 ili N=7. */
                    for (int i = 0; i < N; i++) quorum.addInt(i);
                }
            }
        }
        Util.println("Maekawa quorum for " + myId + " = " + quorum.toString());
    }

    private void fillQuorum(int[] arr) {
        for (int i = 0; i < arr.length; i++) {
            if (!quorum.containsInt(arr[i])) {
                quorum.addInt(arr[i]);
            }
        }
    }

    static class Request implements Comparable<Request> {
        int pid;
        int ts;

        Request(int pid, int ts) {
            this.pid = pid;
            this.ts = ts;
        }

        @Override
        public int compareTo(Request r) {
            if (this.ts != r.ts) return this.ts - r.ts;
            return this.pid - r.pid;
        }
    }
}
