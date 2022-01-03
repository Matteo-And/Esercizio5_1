import java.io.*; 

public class Lexer {

    public static int line = 1;
    private char peek = ' ';
    
    private void readch(BufferedReader br) {
        try {
            peek = (char) br.read();
        } catch (IOException exc) {
            peek = (char) -1; // ERROR
        }
    }

    public Token lexical_scan(BufferedReader br) {

         while (peek == ' ' || peek == '\t' || peek == '\n'  || peek == '\r') {
            if (peek == '\n') line++;
            readch(br);
        }   

   
        
        switch (peek) {
            case '!':
                peek = ' ';
                return Token.not;

	// ... gestire i casi di ( ) { } + - * ; , ... //
    //gestire //, /*, /
            case '(':
                peek = ' ';
                return Token.lpt;
            case ')':
                peek = ' ';
                return Token.rpt;
            case '{':
                peek = ' ';
                return Token.lpg;
            case '}':
                peek = ' ';
                return Token.rpg;
            case '+':
                peek = ' ';
                return Token.plus;
            case '-':
                peek = ' ';
                return Token.minus;
            case '*':
                peek = ' ';
                return Token.mult;
            case ';':
                peek = ' ';
                return Token.semicolon;
            case ',':
                peek = ' ';
                return Token.comma;
	
            case '&':
                readch(br);
                if (peek == '&') {
                    peek = ' ';
                    return Word.and;
                } else {
                    System.err.println("Erroneous character"
                            + " after & : "  + peek );
                    return null;
                }
            case '/':
                    readch(br);
                    if(peek == '/'){
                        while(peek!=(char)-1 && peek!='\n')readch(br);
                        return lexical_scan(br);
                    }else if(peek=='*'){
                        char c=' ';
                        readch(br);
                        while(( !(c== '*' && peek=='/') ) && peek!=(char)-1){
                            if(peek=='\n')line++;
                            c=peek;
                            readch(br); 
                        }if(peek==(char)-1) {   
                            System.err.println("Error comment not terminated" );
                            return null;
                        };
                        readch(br);
                        return lexical_scan(br);
                    }else{
                        return Token.div;
                    }      

	// ... gestire i casi di || < > <= >= == <> ... //
            case '|':
                readch(br);
                if (peek == '|') {
                    peek = ' ';
                    return Word.or;
                }else{
                    System.err.println("Erroneous character"
                    + " after | : "  + peek );
                    return null;
                }
            case '<':
                readch(br);
                if (peek == '=') {
                    peek = ' ';
                    return Word.le;
                } else if(peek=='>'){
                    peek = ' ';
                    return Word.ne;
                }else{
                    peek=' ';
                    return Word.lt;
                }
            case '>':
                readch(br);
                if (peek == '=') {
                    peek = ' ';
                    return Word.ge;
                } else{
                    peek=' ';
                    return Word.gt;
                }
            case '=':
                readch(br);
                if (peek == '=') {
                    peek = ' ';
                    return Word.eq;
                }else{
                    System.err.println("Erroneous character"
                    + " after = : "  + peek );
                    return null;
                }
            case (char)-1:
                return new Token(Tag.EOF);

            default:
            	// ... gestire il caso degli identificatori e delle parole chiave //

                if (Character.isLetter(peek) || peek=='_') {
                String s="";
                boolean status=false;

                while( peek=='_' || Character.isLetter(peek)|| Character.isDigit(peek)){
                    if(peek!='_') status=true;
                    s+=peek;
                    readch(br);
                }
                if(status==false){
                        System.err.println("Error, identifier cannot contain only _ " 
                                + peek );
                        return null;
                    }
                
                    switch(s){
                        case "assign":
                            return Word.assign;
                        case "to":
                            return Word.to;
                        case "if":
                            return Word.iftok;
                        case "else":
                            return Word.elsetok;
                         case "while":
                            return Word.whiletok;
                        case "begin":
                            return Word.begin;
                        case "end":
                            return Word.end;
                        case "print":
                            return Word.print;
                        case "read":
                            return Word.read;
                        default:
                            return new Word(257, s);
                    }
                }else if (Character.isDigit(peek)) {
	            // ... gestire il caso dei numeri ... //
                String s = "";
                    if (peek == '0') {
                        readch(br);
                        if (Character.isDigit(peek)) {
                            System.err.println("Erroneous character after 0: " + peek);
                            return null;
                        }
                        return new NumberTok(0);                
                    }else while(Character.isDigit(peek)){
                            s+=peek;
                            readch(br);
                    }
                    return new NumberTok(Integer.parseInt(s));

                } else {
                        System.err.println("Erroneous character: " + peek );
                        return null;
                }
        }
    }
		
    public static void main(String[] args) {
        Lexer lex = new Lexer();
        String path = "./file_test"; // il percorso del file da leggere
        try {
            BufferedReader br = new BufferedReader(new FileReader(path));
            Token tok;
            do {
                tok = lex.lexical_scan(br);
                System.out.println("Scan: " + tok);
            } while (tok.tag != Tag.EOF);
            br.close();
        } catch (IOException e) {e.printStackTrace();}    
    }

}
