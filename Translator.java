import java.io.*;

public class Translator {
    private Lexer lex;
    private BufferedReader pbr;
    private Token look;

    SymbolTable st = new SymbolTable();
    CodeGenerator code = new CodeGenerator();
    int count=0;

    public Translator(Lexer l, BufferedReader br) {
        lex = l;
        pbr = br;
        move();
    }

    void move() {
        look = lex.lexical_scan(pbr);
    }

    void error(String s) {
    	throw new Error("near line " + lex.line + ": " + s);
    }

    void match(int t) {
      if (look.tag == t) {
          if (look.tag != Tag.EOF) move();
      } else error("syntax error");
    }

    public void prog() {
        switch (look.tag) {
            case Tag.ASSIGN:
            case Tag.PRINT:
            case Tag.READ:
            case Tag.WHILE:
            case Tag.IF:
            case '{':
                int lnext_prog = code.newLabel();//0
                statlist(lnext_prog);
                code.emitLabel(lnext_prog);
                match(Tag.EOF);
                try {
                    code.toJasmin();
                }
                catch (java.io.IOException e) {
                    System.out.println("IO error\n");
                }
                break;
            default:
                error("Error in prog");
        }
    }

    public void statlist(int prevLabel) {
        switch (look.tag) {
            case Tag.ASSIGN:
            case Tag.PRINT:
            case Tag.READ:
            case Tag.WHILE:
            case Tag.IF:
            case '{':
                //SL -> S SL'
                int lstat= code.newLabel(); //1
                stat(lstat);
                code.emitLabel(lstat);
                statlistp(prevLabel);
                code.emit(OpCode.GOto, prevLabel);
                break;
            default:
                error("Error in statlist");
        }
    }
    public void statlistp(int prevLabel) {  //L2:
        switch (look.tag) {
            case ';':
                //SL'->;S SL'
                match(';');
                int lnext= code.newLabel(); //label prossima istruzione
                stat(lnext);
                code.emitLabel(lnext);
                statlistp(prevLabel);
                break;
            case '}':
            case Tag.EOF:
                //SL'->nullo
                code.emit(OpCode.GOto, prevLabel);
                break;

            default:
                error("Error in statlist");
        }
    }
     
    public void stat(int lnext) {
        switch (look.tag) {
            case Tag.ASSIGN:
                //S->assign E to IL
                match(Tag.ASSIGN);
                expr(); //ldc
                match(Tag.TO);
                idlist(); // istore
                code.emit(OpCode.GOto, lnext);
                break;
            case Tag.PRINT:
                //S-> PRINT(EL)
                match(Tag.PRINT);
                match('(');
                exprlist(); 
                match(')');
                code.emit(OpCode.invokestatic, 1);
                code.emit(OpCode.GOto, lnext);
                break;
            case Tag.READ:
                //S->READ(IDL)
                match(Tag.READ);
                code.emit(OpCode.invokestatic, 0); //invokestatic read()
                match('(');
                idlist();
                match(')');
                code.emit(OpCode.GOto, lnext); //GOTO L?
                break;

            case Tag.WHILE:
                //S->while(BE)S
                int lwhile;
                match(Tag.WHILE);
                if (lnext == 1) {
                    lwhile = code.newLabel();
                    code.emitLabel(lwhile);
                } else {
                    lwhile = lnext - 1;
                }
                match('(');
                bexpr(lnext);
                match(')');
                stat(lwhile);
                //code.emitLabel(lnext);
                break;
            case Tag.IF:
                //if(BE) S S' end
                //if(< x 0) {...}
                /*
                    iload 0
                    ilaod x
                    isub
                    if_lt stat():
                    statp()
                                 
                    
                    if
                        bexpr(lstatp) < x 0
                        if_ge lstatp
                        stat(lstat)
                    lstatp:
                    else
                        statp(lstat) --> goto lstat
                    end
                
                    Lstat:
                
                
                */
                match(Tag.IF);
                match('(');
                int lstatp = code.newLabel(); //label dell'else (o nullo)
                bexpr(lstatp);
                match(')');
                stat(lnext);
                code.emitLabel(lstatp);
                statp(lnext);
                match(Tag.END);
                break;
            case '{':
                //S->{SL}
                match('{');
                statlist(lnext);
                match('}');
                break;
            default:
                error("error in stat");
        }
     }

    private void statp(int lnext) {
        switch(look.tag) {
            case Tag.ELSE:
                //S' -> else S
                match(Tag.ELSE);
                stat(lnext);
                break;
            case Tag.END:
                //S' -> nullo
                code.emit(OpCode.GOto, lnext);
                break;
            default:
                error("error in statp");
        }
    }

    private void idlist() {
        switch (look.tag) {
            case Tag.ID:
                //IDL->ID IDL'
                int id_addr = st.lookupAddress(((Word) look).lexeme);
                if (id_addr == -1) {
                    id_addr = count;
                    st.insert(((Word) look).lexeme, count++);
                }
                match(Tag.ID);
                code.emit(OpCode.istore, id_addr);
                idlistp();
                break;
            default:
                error("error in idlist");
        }
    }

    private void idlistp() {
        switch (look.tag) {
            case ',':
                //IDL' -> , ID IDL'
                match(',');
                
                int id_addr = st.lookupAddress(((Word) look).lexeme);
                    if (id_addr == -1) {
                        id_addr = count;
                        st.insert(((Word) look).lexeme, count++);
                    }
                match(Tag.ID);
                code.emit(OpCode.istore, id_addr);
                idlistp();
                break;
            case ')': 
            case Tag.ELSE:
            case Tag.END:
            case '}':
            case ';':
            case Tag.EOF:
                //IDL' -> nullo
                break;
            default:
                error("error in idlistp");
        }
    }

    private void bexpr(int lfalse) { //label a cui andare se è false 
       switch (look.tag) {
           case Tag.RELOP: //BE -> RELOP EE
               switch (((Word) look).lexeme) {
                   case "<":
                    code.emit(OpCode.if_icmpge,lfalse);
                       break;
                   case ">":
                    code.emit(OpCode.if_icmple,lfalse);
                       break;
                   case "<=":
                    code.emit(OpCode.if_icmpgt,lfalse);
                       break;
                   case ">=":
                    code.emit(OpCode.if_icmplt,lfalse);
                       break;
                   case "<>":
                    code.emit(OpCode.if_icmpeq,lfalse);
                       break;
                   case "==":
                    code.emit(OpCode.if_icmpne, lfalse);
                       break;       
                      
                }
                match(Tag.RELOP);
                expr();
                expr();
            break;
            default:
                error("error in bexpr");
        }
    }

    private void expr() {
        switch (look.tag) {
            // ... completare ...

            case '+':
                //E->+(EL)
                match('+');
                match('(');
                int iadd_n=exprlist();
                match(')');
                if(iadd_n!=1)code.emit(OpCode.iadd);
                break;
            case '-':
                //E-> - E E
                match('-');
                expr();
                expr();
                code.emit(OpCode.isub);
                break;
            case '*': //E-> *(EL)
                match('*');
                match('(');
                int imul_n=exprlist();
                match(')');
                if(imul_n!=1)code.emit(OpCode.imul);
                break;
            case '/': //E-> / E E
                match('/');
                match('(');
                exprlist();
                match(')');
                code.emit(OpCode.idiv);
                break;
            case Tag.NUM:
                code.emit(OpCode.ldc, ((NumberTok) look).lexeme);
                match(Tag.NUM);
                break;
            case Tag.ID:
                int id_addr = st.lookupAddress(((Word) look).lexeme);
                if (id_addr == -1) {
                    error("id does not exist");
                }
                code.emit(OpCode.iload, id_addr);
                match(Tag.ID);
                break;
            default:
                error("error in expr");
        }
    }

     private int exprlist() { //ritorna il numero di valori di exprlist
         int exprlist_n=0;
         switch (look.tag) {
            case '+':
            case '-':
            case '*':
            case '/':
            case Tag.NUM:
            case Tag.ID:
                //EL -> E EL'
                expr();
                exprlist_n++; 
                exprlist_n+=exprlistp();
                return exprlist_n;
            default:
                error("error in exprlist");
                return 0;
        }
    }
    // +(3,2,5)
    private int exprlistp() {
        int exprlistp_n = 0;
        switch (look.tag) {
            case ',':
                //EL' -> , E EL'
                match(',');
                expr();
                exprlistp_n++; 
                exprlistp_n+=exprlistp(); 
                return exprlistp_n;
        case ')':
            //EL-> nullo 
            return 0;  
        default:
            error("error in exprlistp");
            return 0;
    }
    }
   
 public static void main(String[] args){
      Lexer lex = new Lexer();
      String path = "./file_test.txt";
      try{
        BufferedReader br = new BufferedReader(new FileReader(path));
        Translator translator = new Translator(lex,br);
        translator.prog();
        br.close();
      } catch (IOException e) { e.printStackTrace();}
    }
}