import java.util.*;
 
/*
 * Suzuki-Kasamijev broadcast algoritam za distribuirano medjusobno iskljucivanje.
 * Zeton (token) sadrzi red cekanja Q i niz LN; RN[] pamti najveci primljeni
 * redni broj zahtjeva od svakog procesa. Ne koristi se poseban REPLY - sam
 * zeton je dopustenje.
 */
public class SuzukiKasamiMutex extends Process implements Lock {
 
    private final int[] RN;
    private final int[] LN;
    private final LinkedList<Integer> Q = new LinkedList<>();
    private boolean haveToken;
    private boolean executing = false;
 
    /**
     * @param initialTokenHolder id procesa koji na pocetku posjeduje zeton
     */
    public SuzukiKasamiMutex(Linker initComm, int initialTokenHolder) {
        super(initComm);
        RN = new int[N];
        LN = new int[N];
        haveToken = (myId == initialTokenHolder);
    }
 
    @Override
    public synchronized void requestCS() {
        RN[myId]++;
        if (!haveToken) {
            for (int i = 0; i < N; i++)
                if (i != myId) sendMsg(i, "sk_request", RN[myId]);
            while (!haveToken) myWait();
        }
        executing = true;
    }
 
    @Override
    public synchronized void releaseCS() {
        executing = false;
        LN[myId] = RN[myId];
 
        for (int j = 0; j < N; j++)
            if (j != myId && !Q.contains(j) && RN[j] == LN[j] + 1)
                Q.add(j);
 
        if (!Q.isEmpty()) {
            int next = Q.removeFirst();
            haveToken = false;
            sendMsg(next, "sk_token", encodeToken());
        }
    }
 
    @Override
    public synchronized void handleMsg(Msg m, int src, String tag) {
        if (tag.equals("sk_request")) {
            int sn = m.getMessageInt();
            RN[src] = Math.max(RN[src], sn);
            // ako imamo slobodan zeton i zahtjev nije zastario, proslijedi ga odmah
            if (haveToken && !executing && RN[src] == LN[src] + 1) {
                haveToken = false;
                sendMsg(src, "sk_token", encodeToken());
            }
        } else if (tag.equals("sk_token")) {
            decodeToken(m.getMessage());
            haveToken = true;
            notify();
        }
    }
 
    private String encodeToken() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < N; i++) sb.append(LN[i]).append(" ");
        sb.append(Q.size()).append(" ");
        for (int q : Q) sb.append(q).append(" ");
        return sb.toString();
    }
 
    private void decodeToken(String msg) {
        StringTokenizer st = new StringTokenizer(msg);
        for (int i = 0; i < N; i++) LN[i] = Integer.parseInt(st.nextToken());
        int qSize = Integer.parseInt(st.nextToken());
        Q.clear();
        for (int k = 0; k < qSize; k++) Q.add(Integer.parseInt(st.nextToken()));
    }
}