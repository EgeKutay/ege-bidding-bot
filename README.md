# ege-bidding-bot

A two-phase adaptive bidding bot for the Playtech Summer 2026 Attention Bid challenge.

## Architecture

The bot consists of 4 classes:

- **BiddingBot** - Main entry point. Reads bid requests from stdin, outputs bids to stdout.
- **BidRequest** - Parses incoming bid request lines using `indexOf`/`substring` (no regex) for fast parsing.
- **Scorer** - Scores each bid request based on category match, view count, subscriber status, and viewer age. Higher scores mean the impression is worth bidding more on.
- **BudgetManager** - Two-phase budget strategy with adaptive bid unit adjustments.

## Strategy

### Phase 1: Spend to 30% floor

- Targets spending 30% of total budget to satisfy the score denominator floor (`score = points / max(spent, 0.3 * budget)`).
- Uses pacing ratio to adjust bid unit by flat +/-1 every 100-round window.
- If underspending vs target pace, raises bid. If overspending with declining PPE, lowers bid.

### Phase 2: PPE optimization

- After reaching 30% spend, shifts to pure Points-Per-Ebuck optimization.
- Carries over bid unit from Phase 1 (capped at entry value, can only drift down).
- Requires 3 consecutive zero-win or PPE-drop windows before adjusting, preventing oscillation.
- Every cheap win in the ascending auction improves score since the denominator is already satisfied.

### Safety floor

- Reserves 5% of budget as emergency buffer. When hit, bids {1, 15} to pick up only the cheapest wins.

### Scoring multipliers

- **Category**: Strongest signal. Video category + interest position drives multipliers from 0.7 (no match) to 2.0 (video + 1st interest match).
- **View count**: Sweet spot at 100K-10M views (1.10-1.25x).
- **Subscribed**: Y = 1.20x, N = 0.80x.
- **Age**: Peak at 25-34 (1.15x), tapering to 0.85x at 55+.

Requires Java 8+.

## Building

### Linux / macOS

```bash
javac -d out src/*.java
echo "Main-Class: BiddingBot" > out/MANIFEST.MF
cd out && jar cfm ../ege-bidding-bot.jar MANIFEST.MF *.class
```

### Windows (PowerShell)

```powershell
javac -d out src\*.java
"Main-Class: BiddingBot" | Out-File -Encoding ascii out\MANIFEST.MF
cd out
jar cfm ..\ege-bidding-bot.jar MANIFEST.MF *.class
cd ..
```

## Running

Place `ege-bidding-bot.jar` in a subdirectory of the harness working directory and run:

### Linux / macOS

```bash
java -jar harness.jar
```

### Windows (PowerShell)

```powershell
java -jar harness.jar
```
