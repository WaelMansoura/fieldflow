package com.fieldflow.regles;

/**
 * Résultat d'une règle : acceptée, ou refusée avec un code d'erreur (contracts/api-rest.md).
 * INVALIDE → 400, INCONNU → 404, MAUVAIS_ETAT → 409.
 */
public record Decision(Type type, String code, String message) {

    public enum Type { OK, INVALIDE, MAUVAIS_ETAT, INCONNU }

    private static final Decision OK = new Decision(Type.OK, null, null);

    public static Decision ok() {
        return OK;
    }

    public static Decision invalide(String code, String message) {
        return new Decision(Type.INVALIDE, code, message);
    }

    public static Decision mauvaisEtat(String code, String message) {
        return new Decision(Type.MAUVAIS_ETAT, code, message);
    }

    public static Decision inconnu(String code, String message) {
        return new Decision(Type.INCONNU, code, message);
    }

    public boolean estOk() {
        return type == Type.OK;
    }
}
