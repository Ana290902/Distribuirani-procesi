public class LockTester {
    public static void main(String[] args) throws Exception {
        Linker comm = null;
        try {
            if (args.length < 4) {
                System.out.println("Usage: java LockTester <baseName> <myId> <numProc> <Singhal|Maekawa>");
                return;
            }

            String baseName = args[0];
            int myId = Integer.parseInt(args[1]);
            int numProc = Integer.parseInt(args[2]);
            String algorithm = args[3];

            comm = new Linker(baseName, myId, numProc);
            Lock lock = null;

            switch (algorithm) {
                case "Singhal" -> lock = new SinghalMutex(comm);
                case "Maekawa" -> lock = new MaekawaMutex(comm);
                default -> {
                    System.out.println("Unknown algorithm: " + algorithm);
                    System.out.println("Allowed values: Singhal, Maekawa");
                    comm.close();
                    return;
                }
            }

            for (int i = 0; i < numProc; i++)
                if (i != myId)
                    (new ListenerThread(i, (MsgHandler) lock)).start();

            while (true) {
                System.out.println("Process " + myId + " is not in CS");
                Util.mySleep(outsideDelay(myId, algorithm));

                System.out.println("Process " + myId + " requests CS");
                lock.requestCS();

                System.out.println("Process " + myId + " ENTERS CS *****");
                Util.mySleep(insideDelay(myId, algorithm));
                System.out.println("Process " + myId + " EXITS CS");

                lock.releaseCS();
            }
        } catch (InterruptedException e) {
            if (comm != null) comm.close();
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
            if (comm != null) comm.close();
        }
    }

    private static int outsideDelay(int myId, String algorithm) {
        if (algorithm.equals("Singhal")) {
            if (myId == 0) return 600;    // proces 0 često traži CS
            if (myId == 1) return 1800;   // proces 1 srednje često
            if (myId == 2) return 4500;   // proces 2 rijetko
            return 2500 + 500 * myId;
        }

        return 2000 + 400 * myId;
    }

    private static int insideDelay(int myId, String algorithm) {
        if (algorithm.equals("Singhal")) {
            if (myId == 0) return 400;    // kratko ostaje u CS
            if (myId == 1) return 1300;   // srednje dugo
            if (myId == 2) return 2600;   // dugo ostaje u CS
            return 1200;
        }

        return 1500;
    }
}
