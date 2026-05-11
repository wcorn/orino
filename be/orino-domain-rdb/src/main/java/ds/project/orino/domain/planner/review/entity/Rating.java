package ds.project.orino.domain.planner.review.entity;

public enum Rating {
    AGAIN(0),
    HARD(3),
    GOOD(4),
    EASY(5);

    private final int qScore;

    Rating(int qScore) {
        this.qScore = qScore;
    }

    public int getQScore() {
        return qScore;
    }
}
