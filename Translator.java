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
                idlist(Tag.ASSIGN); // istore
                code.emit(OpCode.GOto, lnext);
                break;
            case Tag.PRINT:
                //S-> PRINT(EL)
                match(Tag.PRINT);
                match('(');
                exprlist(Tag.PRINT); 
                match(')');
               code.emit(OpCode.invokestatic, 1);
                code.emit(OpCode.GOto, lnext);
                break;
            case Tag.READ:
                //S->READ(IDL)
                match(Tag.READ);
                code.emit(OpCode.invokestatic, 0); //invokestatic read()
                match('(');
                idlist(Tag.READ);
                match(')');
                code.emit(OpCode.GOto, lnext); //GOTO L?
                break;

            case Tag.WHILE:
                //S->while(BE)S
                int lwhile;
                match(Tag.WHILE);
                    lwhile = code.newLabel();
                    code.emitLabel(lwhile);
                match('(');
                bexpr(lnext);
                match(')');
                stat(lwhile);
                //code.emitLabel(lnext);
                break;
            case Tag.IF:
                //if(BE) S S' end
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

    private void idlist(int prevTag) {
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
                idlistp(prevTag,id_addr);
                break;
            default:
                error("error in idlist");
        }
    }

    private void idlistp(int prevTag, int prec_addr) {
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

                if (prevTag == Tag.READ)
                    code.emit(OpCode.invokestatic, 0);

                else if (prevTag == Tag.ASSIGN) {
                    code.emit(OpCode.iload, prec_addr);
                }
                code.emit(OpCode.istore, id_addr);
                idlistp(prevTag, id_addr);
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
        OpCode relop;
        switch (look.tag) {
           case Tag.RELOP: //BE -> RELOP EE
               switch (((Word) look).lexeme) {
                   case "<":
                    relop=OpCode.if_icmpge;
                       break;
                   case ">":
                    relop=OpCode.if_icmple;
                       break;
                   case "<=":
                    relop=OpCode.if_icmpgt;
                       break;
                   case ">=":
                       relop = OpCode.if_icmplt;
                       break;
                   case "<>":
                       relop = OpCode.if_icmpeq;
                       break;
                   case "==":
                       relop = OpCode.if_icmpne;
                       break;
                   default:
                       error(look.tag + "relop does not exist");
                       relop = null;
                }
                match(Tag.RELOP);
                expr();
                expr();
                code.emit(relop,lfalse);
            break;
            default:
                error("error in bexpr");
        }
    }

    private void expr() {
        switch (look.tag) {

            case '+':
                //E->+(EL)
                match('+');
                match('(');
                int iadd_n = exprlist(look.tag);
                match(')');
                while (iadd_n-1 > 0) {
                    code.emit(OpCode.iadd);
                    iadd_n--;
                }
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
                int imul_n = exprlist(look.tag);
                match(')');
                
                while (imul_n-1 > 0) {
                    code.emit(OpCode.imul);
                    imul_n--;
                }
                break;
            case '/': //E-> / E E
                match('/');
                expr();
                expr();
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

     private int exprlist(int prevTag) { //ritorna il numero di valori di exprlist
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
                exprlist_n += exprlistp(prevTag);
                return exprlist_n;
            default:
                error("error in exprlist");
                return 0;
        }
    }
    // +(3,2,5)
    private int exprlistp(int prevTag) {
        int exprlistp_n = 0;
        switch (look.tag) {
            case ',':
                //EL' -> , E EL'
                match(',');
                if (prevTag == Tag.PRINT)
                    code.emit(OpCode.invokestatic, 1);

                expr();
                exprlistp_n++; 
                exprlistp_n+=exprlistp( prevTag); 
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