package edu.campusnexo.application;

public class AiServiceUnavailableException extends RuntimeException {
    public AiServiceUnavailableException() { super("El servicio de IA no está disponible; inténtalo de nuevo más tarde."); }
    public AiServiceUnavailableException(Throwable cause) {
        super("El servicio de IA no está disponible; inténtalo de nuevo más tarde.", cause);
    }
}
