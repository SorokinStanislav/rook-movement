package org.example.rook;

import java.util.Scanner;

public class RookMovementRunner {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        int rooksNumber = scanner.nextInt();
        RookMovement rookMovement = new RookMovement(rooksNumber);
        rookMovement.move();
    }
}
