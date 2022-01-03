public class NumberTok extends Token {
    public int lexeme;
    public NumberTok(int n) {super(256); lexeme=n;}

    public String toString() {
        return "<" + tag + ", " + lexeme + ">";
    }

}