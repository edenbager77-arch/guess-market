package guessmarket.ui;

import guessmarket.engine.service.GuessMarketEngine;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        GuessMarketEngine engine = new GuessMarketEngine();
        ConsoleUI ui = new ConsoleUI(engine, scanner);
        ui.run();
    }
}
