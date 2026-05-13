# Distribuirani-procesi

Softversko rjesenje za projekt: Singhalov i Maekawin algoritam
===============================================================

Projekt je poslozen tako da sadrzi samo datoteke potrebne za pokretanje
LockTester programa s algoritmima Singhal i Maekawa.

Datoteke preuzete iz skripte / distProg.zip:
- Connector.java
- ListenerThread.java
- Msg.java
- NameServer.java
- Process.java
- Util.java
- IntLinkedList.java
- Lock.java
- MsgHandler.java
- NameTable.java
- Symbols.java
- LamportClock.java
- Linker.java
- Name.java
- PortAddr.java
- Topology.java

Datoteke dodane za projekt:
- SinghalMutex.java
- MaekawaMutex.java

Minimalno prilagodena datoteka:
- LockTester.java
  U njoj su ostavljene samo dvije opcije: Singhal i Maekawa.

Pokretanje na Linuxu
--------------------
1. Udi u mapu projekta:
   Distribuirani-procesi

2. Prevedi sve datoteke:
   javac *.java

3. U posebnom terminalu pokreni NameServer:
   java NameServer

4. Za Singhalov algoritam otvori 3 nova terminala i pokreni:

   java LockTester TestSinghal 0 3 Singhal

   java LockTester TestSinghal 1 3 Singhal

   java LockTester TestSinghal 2 3 Singhal

5. Za Maekawin algoritam prvo zaustavi sve procese i NameServer s CTRL+C,
   zatim ponovno pokreni NameServer i u 3 terminala pokreni:

   java LockTester TestMaekawa 0 3 Maekawa

   java LockTester TestMaekawa 1 3 Maekawa
   
   java LockTester TestMaekawa 2 3 Maekawa

Napomena
--------
Ako ponovno pokreces isti test, najbolje je ponovno pokrenuti NameServer ili
promijeniti bazno ime, npr. TestSinghal2.
