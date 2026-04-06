/**
 * Scorer with calibrated multipliers.
 *
 * Based on observed points-per-round variance (5.95–9.48, 60% spread).
 * Multipliers are moderate reflecting real but not extreme field effects.
 *
 * Score range: [0.75, 1.60] (MIN_SCORE to MAX_SCORE factors)
 */
public class Scorer {

    private final String category;

    private static final double MIN_SCORE = 0.70;
    private static final double MAX_SCORE = 2.4;

    public Scorer(String category) {
        this.category = category;
    }

    public double score(BidRequest req) {
        double value = 1.0;

        // Category match
        value *= getCategoryMultiplier(req);

        // View count: 0.9 – 1.25
        value *= getViewCountMultiplier(req.viewCount);

        // Subscribed
        value *= "Y".equals(req.subscribed) ? 1.20 : 0.8;

        // Age: 25-34 is peak, drops off both directions
        value *= getAgeMultiplier(req.age);

        return Math.max(MIN_SCORE, Math.min(MAX_SCORE, value));
    }

    /**
     * Category match multiplier based on video category + interest position.
     *
     * Video matches + interest 1st:  1.6
     * Video matches + interest 2nd:  1.5
     * Video matches + interest 3rd or not in interests: 1.4
     * No video match + interest present: 1.1
     * No match at all: 0.9
     */
    private double getCategoryMultiplier(BidRequest req) {
        boolean videoMatch = category.equals(req.videoCategory);
        int interestPos = req.getInterestPosition(category); // 0,1,2 = found, 3 = not found

        if (videoMatch) {
            if (interestPos == 0) return 2.0;
            if (interestPos == 1) return 1.85;
            return 1.80; // video match, interest 3rd or absent
        }

        if (interestPos < 2) return 1.10; // no video match, but in interests. 3rd placement is almost same as if nothing here.
        return 0.7; // no match at all
    }

    /**
     * View count: 0.9 – 1.2
     * Sweet spot at 100K-10M range.
     */
    private double getViewCountMultiplier(long viewCount) {
        if (viewCount < 10_000)             return 0.90;
        if (viewCount < 100_000)            return 1.10;
        if (viewCount < 1_000_000)          return 1.25;
        if (viewCount < 10_000_000)         return 1.15;
        if (viewCount < 100_000_000)        return 1.05;
        if (viewCount < 1_000_000_000)      return 0.95;
        return 0.90;
    }

    private double getAgeMultiplier(String age) {
        if (age == null) return 1.0;
        switch (age) {
            case "18-24": return 1.05;
            case "25-34": return 1.15;
            case "35-44": return 1.05;
            case "45-54": return 0.95;
            case "55+":   return 0.85;
            default:      return 1.0;
        }
    }

}
