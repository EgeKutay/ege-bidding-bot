import java.io.*;

/**
 * ege-bot-sophisticated-v3
 * simple and yet effective!
 * Two-Phase Bidding Bot with adaptive bidUnit.
 *
 * Phase 1: Spend up to 30% of budget with adaptive bidding.
 * Phase 2: Pure PPE optimization, only bid when it improves score.
 * 
 */
public class BiddingBot {

    private static final String CATEGORY = "Cooking";

    private final BudgetManager budget;
    private final Scorer        scorer;
    private final BidRequest    request;


    public BiddingBot(long initialBudget) {
        this.budget  = new BudgetManager(initialBudget);
        this.scorer  = new Scorer(CATEGORY);
        this.request = new BidRequest();
    }

    public void run() throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        //Disabled autoFlush because it slows down on every \n triggers OS level syscall.
        PrintWriter out = new PrintWriter(
            new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 1024), false
        );

        out.println(CATEGORY);
        out.flush();

        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("S ")) {
                handleSummary(line);
            } else if (line.equals("W") || line.startsWith("W ")) {
                handleWin(line);
            } else if (line.equals("L")) {
                // loss nothing to track
            } else {
                handleBidRequest(line, out);
            }
        }
        out.flush();
    }

    private void handleBidRequest(String line, PrintWriter out) {
        request.parse(line);
        double score = scorer.score(request);
        int[] bid = budget.calculateBid(score);
        out.println(bid[0] + " " + bid[1]);
        out.flush();
        budget.recordRoundPlayed();
    }

    private void handleWin(String line) {
        long ebucksSpent = 0;
        if (line.length() > 2) {
            try { ebucksSpent = Long.parseLong(line.substring(2).trim()); }
            catch (NumberFormatException e) {}
        }
        budget.recordWin(ebucksSpent);
    }

    private void handleSummary(String line) {
        String[] parts = line.split(" ");
        if (parts.length < 3) return;
        try {
            long points = Long.parseLong(parts[1]);
            long ebucks = Long.parseLong(parts[2]);
            budget.recalibrate(points, ebucks);
        } catch (NumberFormatException e) {}
    }

    public static void main(String[] args) {
        long budgetAmount = args.length > 0 ? Long.parseLong(args[0]) : 10_000_000;
        BiddingBot bot = new BiddingBot(budgetAmount);
        try { bot.run(); }
        catch (IOException e) { System.err.println("[Bot] Error: " + e.getMessage()); }
    }
}
