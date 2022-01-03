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
    public void statlistp(int prevLabel) {  ///L2:
        switch (look.tag) {
            case ';':
                //SL'->;S SL'
                match(';');
                int lstat= code.newLabel(); //2
                stat(lstat);
                code.emitLabel(lstat);
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
     
    public void stat(int lstat) { //GOto
        switch(look.tag) {
            case Tag.ASSIGN:
              //S->assign E to IL
              match(Tag.ASSIGN);
              expr(); //ldc
              match(Tag.TO);
              idlist(); // istore
              code.emit(OpCode.GOto,lstat);
              break;
            case Tag.PRINT:
              //S-> PRINT(EL)
              match(Tag.PRINT);
              match('(');
              exprlist(); //iload -ldc - iadd
              match(')');
              code.emit(OpCode.invokestatic,1);
              code.emit(OpCode.GOto,lstat);
              break;
          case Tag.READ:
                //S->(IDL)
                match(Tag.READ);
                code.emit(OpCode.invokestatic, 0); //invokestatic read()
                match('(');
	            idlist();
                match(')');
                code.emit(OpCode.GOto,lstat); //GOTO L?
                break;
            
            case Tag.WHILE:
                //S->while(BE)S
                int lwhile;
                if (lstat == 1) {
                    lwhile = code.newLabel();
                    code.emitLabel(lwhile); 
                } else {
                    lwhile=lstat-1;
                }
                match(Tag.WHILE);
                match('(');
                boolean bwhile=bexpr(); 
                match(')');
                if(bwhile==true) stat(lstat);
                code.emit(OpCode.GOto,lwhile);
                break;
            case Tag.IF:
              //if(BE) S S' end
              match(Tag.IF);
              match('(');
              boolean bif = bexpr();
              match(')');
              if(bif==true) stat(lstat);
              statp(bif,lstat);
              match(Tag.END);
              break;
            case '{':
              //S->{IL}
              match('{');
              idlist();
              match('}');
              break;
            default:
              error("error in stat");
        }
     }

    private void idlist() {
        switch(look.tag) {
	    case Tag.ID:
        	int id_addr = st.lookupAddress(((Word)look).lexeme);
                if (id_addr==-1) {
                    id_addr = count;
                    st.insert(((Word)look).lexeme,count++);
                }
                match(Tag.ID);
                code.emit(OpCode.istore);
	    default:
                error("error in idlist");
    	}
    }

    private void expr( /* completare */ ) {
        switch(look.tag) {
	// ... completare ...
            case '-':
                match('-');
                expr();
                expr();
                code.emit(OpCode.isub);
                break;
	// ... completare ...
        }
    }

    // ... completare ...
   
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