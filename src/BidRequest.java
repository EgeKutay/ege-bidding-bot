/**
 * Parsed representation of one bid request line.
 *
 * Input format (fields can appear in ANY order):
 * video.category=Kids,video.viewCount=12345,video.commentCount=987,
 * viewer.subscribed=Y,viewer.age=18-24,viewer.gender=F,
 * viewer.interests=Video Games;Music
 *
 * Parsing is done with indexOf/substring, no regex, no split on full line.
 */
public class BidRequest {

    // Video fields
    public String videoCategory;
    public long    viewCount;
    public long    commentCount;

    // Viewer fields
    public String  subscribed;   // "Y" or "N"
    public String  age;          // e.g. "18-24"
    public String  gender;       // "M" or "F"
    public String[] interests;   // up to 3 interests, ordered by relevance

    /**
     * Parses a bid request line in place.
     * Reuses this object to avoid allocation every round.
     */
    public void parse(String line) {
        // Reset defaults
        videoCategory = null;
        viewCount     = 0;
        commentCount  = 0;
        subscribed    = "N";
        age           = "25-34";
        gender        = "M";
        interests     = null;

        int start = 0;
        int len   = line.length();

        while (start < len) {
            int end = line.indexOf(',', start);
            if (end == -1) end = len;

            int eq = line.indexOf('=', start);
            if (eq == -1 || eq > end) {
                start = end + 1;
                continue;
            }

            String key   = line.substring(start, eq);
            String value = line.substring(eq + 1, end);

            switch (key) {
                case "video.category":     videoCategory = value; break;
                case "video.viewCount":    viewCount = parseLong(value); break;
                case "video.commentCount": commentCount = parseLong(value); break;
                case "viewer.subscribed":  subscribed = value; break;
                case "viewer.age":         age = value; break;
                case "viewer.gender":      gender = value; break;
                case "viewer.interests":   interests = value.split(";"); break;
            }

            start = end + 1;
        }
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Returns position of target category in interests (0-based).
     * Returns 3 if not found.
     */
    public int getInterestPosition(String targetCategory) {
        if (interests == null) return 3;
        for (int i = 0; i < interests.length && i < 3; i++) {
            if (targetCategory.equals(interests[i].trim())) return i;
        }
        return 3;
    }

    @Override
    public String toString() {
        return String.format(
            "BidRequest{cat=%s, views=%d, comments=%d, sub=%s, age=%s, gender=%s}",
            videoCategory, viewCount, commentCount, subscribed, age, gender
        );
    }
}
