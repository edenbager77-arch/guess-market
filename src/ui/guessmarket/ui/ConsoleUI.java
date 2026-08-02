package guessmarket.ui;

import guessmarket.engine.model.GMEvent;
import guessmarket.engine.model.GMOption;
import guessmarket.engine.model.Trade;
import guessmarket.engine.service.GuessMarketEngine;
import guessmarket.engine.service.GuessMarketException;
import guessmarket.engine.service.PurchaseResult;
import guessmarket.engine.xml.EventFileValidationResult;

import java.util.List;
import java.util.Scanner;

/**
 * The "active" UI module: owns main(), the menu loop, and ALL console
 * input/output. It never touches LMSR math directly - everything about
 * event state and pricing comes from the engine.
 */
public class ConsoleUI {

    private final GuessMarketEngine engine;
    private final InputHelper input;
    private boolean running = true;

    public ConsoleUI(GuessMarketEngine engine, Scanner scanner) {
        this.engine = engine;
        this.input = new InputHelper(scanner);
    }

    public void run() {
        System.out.println("Welcome to Guess Market!");
        try {
            while (running) {
                printMenu();
                int choice = input.readIntInRange("Choose a command: ", 1, 6);
                System.out.println();
                try {
                    handleChoice(choice);
                } catch (GuessMarketException e) {
                    System.out.println("Error: " + e.getMessage());
                }
                System.out.println();
            }
        } catch (java.util.NoSuchElementException | IllegalStateException e) {
            // Input stream ended unexpectedly (e.g. redirected/piped input ran out).
            // Exit quietly instead of printing a raw stack trace.
            System.out.println("No more input available. Goodbye!");
        }
    }

    private void printMenu() {
        System.out.println("---------------------------------------");
        System.out.println("1. Load events file");
        System.out.println("2. Display events");
        System.out.println("3. Show event market status");
        System.out.println("4. Participate in an event (buy shares)");
        System.out.println("5. Close an event");
        System.out.println("6. Exit");
        System.out.println("---------------------------------------");
    }

    private void handleChoice(int choice) {
        switch (choice) {
            case 1: loadFile(); break;
            case 2: displayEvents(); break;
            case 3: showMarketStatus(); break;
            case 4: participate(); break;
            case 5: closeEvent(); break;
            case 6: exit(); break;
        }
    }

    private void loadFile() {
        String path = input.readLine("Enter the full path to the XML events file: ");
        EventFileValidationResult result = engine.loadFile(path);
        if (result.isValid()) {
            System.out.println("File loaded successfully. " + engine.getAllEvents().size() + " event(s) loaded.");
        } else {
            System.out.println("The file is invalid. Details:");
            for (String err : result.getErrors()) {
                System.out.println("  - " + err);
            }
            System.out.println("The previously loaded events (if any) were not changed.");
        }
    }

    private void requireFileLoaded() {
        if (!engine.isFileLoaded()) {
            throw new GuessMarketException("No events file has been loaded yet. Please use option 1 first.");
        }
    }

    private void displayEvents() {
        requireFileLoaded();
        List<GMEvent> events = engine.getAllEvents();
        if (events.isEmpty()) {
            System.out.println("There are no events to display.");
            return;
        }
        printEventList(events);
    }

    private void printEventList(List<GMEvent> events) {
        int i = 1;
        for (GMEvent e : events) {
            System.out.println(i + ". [" + (e.isClosed() ? "CLOSED" : "ACTIVE") + "] "
                    + e.getName() + " (id " + e.getId() + ")");
            System.out.println("   Description: " + e.getDescription());
            System.out.println("   Commission: " + e.getCommissionPercent() + "% ("
                    + e.getCommissionType().displayName() + ")");
            System.out.println("   Options: " + e.getOptions().get(0).getName()
                    + " / " + e.getOptions().get(1).getName());
            i++;
        }
    }

    private GMEvent chooseEventFromList(List<GMEvent> events) {
        int selection = input.readIntInRange("Select an event by number: ", 1, events.size());
        return events.get(selection - 1);
    }

    private void showMarketStatus() {
        requireFileLoaded();
        List<GMEvent> events = engine.getAllEvents();
        if (events.isEmpty()) {
            System.out.println("There are no events to display.");
            return;
        }
        printEventList(events);
        GMEvent event = chooseEventFromList(events);
        System.out.println();
        printFullStatus(event);
    }

    private void printCurrentState(GMEvent event) {
        System.out.println("Current market state:");
        for (GMOption opt : event.getOptions()) {
            double price = engine.getPrice(event.getId(), opt.getName());
            System.out.printf("   %-20s value: %.2f   total shares bought: %.2f%n",
                    opt.getName(), price, opt.getTotalSharesBought());
        }
    }

    private void printFullStatus(GMEvent event) {
        System.out.println("Status for event: " + event.getName() + " (id " + event.getId() + ") ["
                + (event.isClosed() ? "CLOSED" : "ACTIVE") + "]");
        printCurrentState(event);
        System.out.printf("Event account balance: %.2f%n", event.getAccount());
        System.out.printf("Total commission collected: %.2f%n", event.getTotalCommissionCollected());

        System.out.println("Trading history (most recent first):");
        List<Trade> history = event.getHistory();
        if (history.isEmpty()) {
            System.out.println("   (no trades yet)");
        } else {
            for (int i = history.size() - 1; i >= 0; i--) {
                Trade t = history.get(i);
                System.out.printf("   Option: %-15s Quantity: %-10.2f Price paid: %.2f%n",
                        t.getOptionName(), t.getQuantity(), t.getPricePaid());
            }
        }

        if (event.isClosed()) {
            System.out.println("Final shares purchased per option:");
            for (GMOption opt : event.getOptions()) {
                String marker = opt.getName().equalsIgnoreCase(event.getWinningOption()) ? "  <-- WINNER" : "";
                System.out.printf("   %-20s %.2f%s%n", opt.getName(), opt.getTotalSharesBought(), marker);
            }
        }
    }

    private void participate() {
        requireFileLoaded();
        List<GMEvent> active = engine.getActiveEvents();
        if (active.isEmpty()) {
            System.out.println("There are no active events available for trading.");
            return;
        }
        printEventList(active);
        GMEvent event = chooseEventFromList(active);
        System.out.println();
        printCurrentState(event);
        System.out.println();

        System.out.println("Options:");
        for (int i = 0; i < event.getOptions().size(); i++) {
            System.out.println("   " + (i + 1) + ". " + event.getOptions().get(i).getName());
        }
        int optionIndex = input.readIntInRange("Choose the option you want to buy: ", 1, event.getOptions().size());
        double quantity = input.readPositiveDouble("Enter the quantity of shares to buy: ");

        PurchaseResult result = engine.purchase(event.getId(), optionIndex, quantity);

        System.out.println();
        System.out.println("Purchase completed.");
        System.out.printf("   Shares cost: %.2f%n", result.getSharesCost());
        if (result.getCommission() > 0) {
            System.out.printf("   Commission:  %.2f%n", result.getCommission());
        }
        System.out.printf("   Total paid:  %.2f%n", result.getTotalPaid());
        System.out.println();
        printCurrentState(event);
    }

    private void closeEvent() {
        requireFileLoaded();
        List<GMEvent> active = engine.getActiveEvents();
        if (active.isEmpty()) {
            System.out.println("There are no active events available to close.");
            return;
        }
        printEventList(active);
        GMEvent event = chooseEventFromList(active);
        System.out.println();
        printFullStatus(event);
        System.out.println();

        System.out.println("Options:");
        for (int i = 0; i < event.getOptions().size(); i++) {
            System.out.println("   " + (i + 1) + ". " + event.getOptions().get(i).getName());
        }
        int winnerIndex = input.readIntInRange("Which option won? ", 1, event.getOptions().size());

        engine.closeEvent(event.getId(), winnerIndex);

        System.out.println();
        System.out.println("Event closed.");
        printFullStatus(event);
    }

    private void exit() {
        System.out.println("Goodbye!");
        running = false;
    }
}
