# Moteur B : concurrence manuelle

**Responsable :** Ayoub Echihami (Membre 4)

`ExecutorService` (N workers, un `KafkaConsumer` chacun), compteurs `ConcurrentHashMap<String, AtomicLong>`, `CyclicBarrier`. Publie dans `stats-b` au même format que `stats-a`.
