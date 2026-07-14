package com.securetrans.domain.enums;

public class Enums {

    public enum UserRole { USER, ADMIN }

    public enum AccountStatus { ACTIVE, FROZEN, SUSPENDED }

    /** Correspond au champ "type" attendu par le modele IA (PaySim-like). */
    public enum TransactionType { CASH_IN, CASH_OUT, DEBIT, PAYMENT, TRANSFER }

    public enum TransactionStatus { PENDING, SUCCESS, BLOCKED, CANCELLED, FORCED }

    public enum AlertStatus { OPEN, ACKNOWLEDGED, RESOLVED }
}
