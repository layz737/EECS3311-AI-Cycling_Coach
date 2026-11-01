package observer;

/** Supports registration and notification of observers. */
public interface Subject {
    /** Registers an observer once; rejects null with IllegalArgumentException. */
    void addObserver(Observer observer);

    /** Removes an observer if registered; null and absent observers are ignored. */
    void removeObserver(Observer observer);

    /** Notifies a snapshot of registered observers synchronously in registration order. */
    void notifyObservers();
}
