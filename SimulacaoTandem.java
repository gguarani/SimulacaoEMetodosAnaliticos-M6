/* 
   Simulação e Métodos Analíticos - 2026/1
   Alunos: Gabrielle Guarani da Silva e Gustavo Filipi Lopes Machado
 */

import java.util.PriorityQueue;
import java.util.Random;
import java.util.HashMap;

public class SimulacaoTandem {

    private static final int NUM_RANDOMS = 100000;
    private static final Random random = new Random(42);
    private static int randomsUsed = 0;

    static class Fila {
        private final int id;
        private final int numServers;
        private final int capacity;
        private final double minArrival;
        private final double maxArrival;
        private final double minService;
        private final double maxService;

        private int customers;
        private int lostCustomers;
        private double lastEventTime;
        private double totalTime;

        private final HashMap<Integer, Double> accumulatedTimeByState;

        public Fila(int id, int numServers, int capacity,
                double minArr, double maxArr,
                double minServ, double maxServ) {

            this.id = id;
            this.numServers = numServers;
            this.capacity = capacity;
            this.minArrival = minArr;
            this.maxArrival = maxArr;
            this.minService = minServ;
            this.maxService = maxServ;

            this.customers = 0;
            this.lostCustomers = 0;
            this.lastEventTime = 0.0;
            this.totalTime = 0.0;

            this.accumulatedTimeByState = new HashMap<>();
            for (int i = 0; i <= capacity; i++) {
                accumulatedTimeByState.put(i, 0.0);
            }
        }

        // Retorna true se o cliente entrou, false se foi perdido
        public boolean in(double time) {
            updateAccumulatedTime(time);

            if (customers < capacity) {
                customers++;
                return true;
            } else {
                lostCustomers++;
                return false;
            }
        }

        public void out(double time) {
            updateAccumulatedTime(time);
            customers--;
        }

        public void updateAccumulatedTime(double time) {
            double delta = time - lastEventTime;

            if (delta > 0) {
                accumulatedTimeByState.put(customers,
                        accumulatedTimeByState.getOrDefault(customers, 0.0) + delta);
                totalTime += delta;
            }

            lastEventTime = time;
        }

        public int getCustomers() {
            return customers;
        }

        public int getNumServers() {
            return numServers;
        }

        public int getLostCustomers() {
            return lostCustomers;
        }

        public double getMinArrival() {
            return minArrival;
        }

        public double getMaxArrival() {
            return maxArrival;
        }

        public double getMinService() {
            return minService;
        }

        public double getMaxService() {
            return maxService;
        }

        public double getTotalTime() {
            return totalTime;
        }

        public void printResults() {
            System.out.println("=====================================");
            System.out.println("Fila " + id);
            System.out.println("=====================================");

            System.out.printf("Tempo total: %.4f%n", totalTime);
            System.out.println("Perdas: " + lostCustomers);

            System.out.println("\nEstados (tempo acumulado):");
            accumulatedTimeByState.entrySet().stream()
                    .sorted(HashMap.Entry.comparingByKey())
                    .forEach(e -> System.out.printf("  Estado %d: %.4f%n", e.getKey(), e.getValue()));

            System.out.println("\nProbabilidades:");
            accumulatedTimeByState.entrySet().stream()
                    .sorted(HashMap.Entry.comparingByKey())
                    .forEach(e -> System.out.printf("  Estado %d: %.4f%%%n",
                            e.getKey(),
                            (e.getValue() / totalTime) * 100));

            System.out.println();
        }
    }

    static class Event implements Comparable<Event> {
        double time;
        String type;
        int queueId;

        public Event(double time, String type, int queueId) {
            this.time = time;
            this.type = type;
            this.queueId = queueId;
        }

        @Override
        public int compareTo(Event o) {
            return Double.compare(this.time, o.time);
        }
    }

    public static void main(String[] args) {

        // Fila 1: G/G/2/3 — 2 servidores, capacidade 3, chegadas 1..4, serviço 3..4
        Fila fila1 = new Fila(1, 2, 3, 1, 4, 3, 4);

        // Fila 2: G/G/1/5 — 1 servidor, capacidade 5, sem chegadas externas, serviço
        // 2..3
        Fila fila2 = new Fila(2, 1, 5, 0, 0, 2, 3);

        PriorityQueue<Event> scheduler = new PriorityQueue<>();

        // Primeiro cliente chega em t=1.5
        scheduler.add(new Event(1.5, "Arrival", 1));

        double globalTime = 0;

        while (!scheduler.isEmpty() && randomsUsed < NUM_RANDOMS) {

            Event e = scheduler.poll();
            globalTime = e.time;

            if (e.type.equals("Arrival")) {

                if (e.queueId == 1) {
                    // Chegada na Fila 1
                    boolean entered = fila1.in(globalTime);

                    // Se entrou e há servidor disponível (customers <= numServers após entrar),
                    // escalonar atendimento
                    if (entered && fila1.getCustomers() <= fila1.getNumServers()) {
                        double service = generateRandom(fila1.getMinService(), fila1.getMaxService());
                        if (service < 0)
                            break;
                        scheduler.add(new Event(globalTime + service, "Departure", 1));
                    }

                    // Sempre agendar próxima chegada externa na Fila 1
                    double arrival = generateRandom(fila1.getMinArrival(), fila1.getMaxArrival());
                    if (arrival < 0)
                        break;
                    scheduler.add(new Event(globalTime + arrival, "Arrival", 1));

                } else {
                    // Chegada na Fila 2 (vinda da Fila 1)
                    boolean entered = fila2.in(globalTime);

                    // Se entrou e há servidor disponível, escalonar atendimento
                    if (entered && fila2.getCustomers() <= fila2.getNumServers()) {
                        double service = generateRandom(fila2.getMinService(), fila2.getMaxService());
                        if (service < 0)
                            break;
                        scheduler.add(new Event(globalTime + service, "Departure", 2));
                    }
                }

            } else {
                // Evento de Saída

                if (e.queueId == 1) {
                    // Saída da Fila 1
                    fila1.out(globalTime);

                    // Enviar para Fila 2 no mesmo instante
                    scheduler.add(new Event(globalTime, "Arrival", 2));

                    // Se ainda há clientes suficientes para ocupar todos servidores, escalonar novo
                    // atendimento
                    if (fila1.getCustomers() >= fila1.getNumServers()) {
                        double service = generateRandom(fila1.getMinService(), fila1.getMaxService());
                        if (service < 0)
                            break;
                        scheduler.add(new Event(globalTime + service, "Departure", 1));
                    }

                } else {
                    // Saída da Fila 2 — cliente deixa o sistema
                    fila2.out(globalTime);

                    // Se ainda há clientes suficientes para ocupar todos servidores, escalonar novo
                    // atendimento
                    if (fila2.getCustomers() >= fila2.getNumServers()) {
                        double service = generateRandom(fila2.getMinService(), fila2.getMaxService());
                        if (service < 0)
                            break;
                        scheduler.add(new Event(globalTime + service, "Departure", 2));
                    }
                }
            }
        }

        // Finalizar contabilização do tempo no estado atual de cada fila
        fila1.updateAccumulatedTime(globalTime);
        fila2.updateAccumulatedTime(globalTime);

        // Resultados
        System.out.println(" ========= FIM DA SIMULAÇÃO ========= ");
        System.out.printf("Tempo global: %.4f%n", globalTime);
        System.out.println("Aleatórios usados: " + randomsUsed);
        System.out.println();

        fila1.printResults();
        fila2.printResults();
    }

    private static double generateRandom(double min, double max) {
        if (randomsUsed >= NUM_RANDOMS)
            return -1;
        randomsUsed++;
        return random.nextDouble() * (max - min) + min;
    }
}
