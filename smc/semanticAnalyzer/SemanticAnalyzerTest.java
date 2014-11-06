package smc.semanticAnalyzer;

import de.bechte.junit.runners.context.HierarchicalContextRunner;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import smc.lexer.Lexer;
import smc.parser.Parser;
import smc.parser.SyntaxBuilder;

import java.util.List;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.IsCollectionContaining.hasItems;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertEquals;
import static smc.parser.FsmSyntax.Header;
import static smc.parser.ParserEvent.EOF;
import static smc.semanticAnalyzer.AbstractSyntaxTree.AnalysisError;
import static smc.semanticAnalyzer.AbstractSyntaxTree.AnalysisError.ID.*;
import static smc.semanticAnalyzer.AbstractSyntaxTree.State;

@RunWith(HierarchicalContextRunner.class)
public class SemanticAnalyzerTest {
  private Lexer lexer;
  private Parser parser;
  private SyntaxBuilder builder;
  private SemanticAnalyzer analyzer;

  @Before
  public void setUp() throws Exception {
    builder = new SyntaxBuilder();
    parser = new Parser(builder);
    lexer = new Lexer(parser);
    analyzer = new SemanticAnalyzer();
  }

  private AbstractSyntaxTree produceAst(String s) {
    lexer.lex(s);
    parser.handleEvent(EOF, -1, -1);
    return analyzer.analyze(builder.getFsm());
  }

  private void assertSemanticResult(String s, String expected) {
    AbstractSyntaxTree abstractSyntaxTree = produceAst(s);
    assertEquals(expected, abstractSyntaxTree.toString());
  }

  public class SemanticErrors {
    public class HeaderErrors {
      @Test
      public void noHeaders() throws Exception {
        List<AnalysisError> errors = produceAst("{}").errors;
        assertThat(errors, hasItems(
          new AnalysisError(NO_ACTIONS),
          new AnalysisError(NO_FSM),
          new AnalysisError(NO_INITIAL)));
      }

      @Test
      public void missingActions() throws Exception {
        List<AnalysisError> errors = produceAst("FSM:f Initial:i {}").errors;
        assertThat(errors, not(hasItems(
          new AnalysisError(NO_FSM),
          new AnalysisError(NO_INITIAL))));
        assertThat(errors, hasItems(new AnalysisError(NO_ACTIONS)));
      }

      @Test
      public void missingFsm() throws Exception {
        List<AnalysisError> errors = produceAst("actions:a Initial:i {}").errors;
        assertThat(errors, not(hasItems(
          new AnalysisError(NO_ACTIONS),
          new AnalysisError(NO_INITIAL))));
        assertThat(errors, hasItems(new AnalysisError(NO_FSM)));
      }

      @Test
      public void missingInitial() throws Exception {
        List<AnalysisError> errors = produceAst("Actions:a Fsm:f {}").errors;
        assertThat(errors, not(hasItems(
          new AnalysisError(NO_ACTIONS),
          new AnalysisError(NO_FSM))));
        assertThat(errors, hasItems(new AnalysisError(NO_INITIAL)));
      }

      @Test
      public void nothingMissing() throws Exception {
        List<AnalysisError> errors = produceAst("Initial: f Actions:a Fsm:f {}").errors;
        assertThat(errors.size(), equalTo(0));
        assertThat(errors, not(hasItems(
          new AnalysisError(NO_INITIAL),
          new AnalysisError(NO_ACTIONS),
          new AnalysisError(NO_FSM))));
      }

      @Test
      public void unexpectedHeader() throws Exception {
        List<AnalysisError> errors = produceAst("X: x{s - - -}").errors;
        assertThat(errors, hasItems(
          new AnalysisError(INVALID_HEADER, new Header("X", "x"))));
      }

      @Test
      public void duplicateHeader() throws Exception {
        List<AnalysisError> errors = produceAst("fsm:f fsm:x{s - - -}").errors;
        assertThat(errors, hasItems(
          new AnalysisError(EXTRA_HEADER_IGNORED, new Header("fsm", "x"))));
      }
    } // Header Errors

    public class StateErrors {
      @Test
      public void nullNextStateIsNotUndefined() throws Exception {
        List<AnalysisError> errors = produceAst("{s - - -}").errors;
        assertThat(errors, not(hasItems(new AnalysisError(UNDEFINED_STATE, null))));
      }

      @Test
      public void undefinedState() throws Exception {
        List<AnalysisError> errors = produceAst("{s - s2 -}").errors;
        assertThat(errors, hasItems(new AnalysisError(UNDEFINED_STATE, "s2")));
      }

      @Test
      public void noUndefinedStates() throws Exception {
        List<AnalysisError> errors = produceAst("{s - s -}").errors;
        assertThat(errors, not(hasItems(new AnalysisError(UNDEFINED_STATE, "s2"))));
      }

      @Test
      public void undefinedSuperState() throws Exception {
        List<AnalysisError> errors = produceAst("{s:ss - - -}").errors;
        assertThat(errors, hasItems(new AnalysisError(UNDEFINED_SUPER_STATE, "ss")));
      }

      @Test
      public void superStateDefined() throws Exception {
        List<AnalysisError> errors = produceAst("{ss - - - s:ss - - -}").errors;
        assertThat(errors, not(hasItems(new AnalysisError(UNDEFINED_SUPER_STATE, "s2"))));
      }

      @Test
      public void unusedStates() throws Exception {
        List<AnalysisError> errors = produceAst("{s - - -}").errors;
        assertThat(errors, hasItems(new AnalysisError(UNUSED_STATE, "s")));
      }

      @Test
      public void noUnusedStates() throws Exception {
        List<AnalysisError> errors = produceAst("{s - s -}").errors;
        assertThat(errors, not(hasItems(new AnalysisError(UNUSED_STATE, "s"))));
      }

      @Test
      public void usedAsBaseIsValidUsage() throws Exception {
        List<AnalysisError> errors = produceAst("{b - - - s:b - s -}").errors;
        assertThat(errors, not(hasItems(new AnalysisError(UNUSED_STATE, "b"))));
      }
    } // State Errors

    public class TransitionErrors {
      @Test
      public void duplicateTransitions() throws Exception {
        List<AnalysisError> errors = produceAst("{s e - - s e - -}").errors;
        assertThat(errors, hasItems(new AnalysisError(DUPLICATE_TRANSITION, "s(e)")));
      }

      @Test
      public void noDuplicateTransitions() throws Exception {
        List<AnalysisError> errors = produceAst("{s e - -}").errors;
        assertThat(errors, not(hasItems(new AnalysisError(DUPLICATE_TRANSITION, "s(e)"))));
      }

      @Test
      public void abstractStatesCantBeTargets() throws Exception {
        List<AnalysisError> errors = produceAst("{(as) e - - s e as -}").errors;
        assertThat(errors, hasItems(new AnalysisError(ABSTRACT_STATE_USED_AS_NEXT_STATE, "s(e)->as")));
      }

      @Test
      public void abstractStatesCanBeUsedAsSuperStates() throws Exception {
        List<AnalysisError> errors = produceAst("{(as) e - - s:as e s -}").errors;
        assertThat(errors, not(hasItems(new AnalysisError(ABSTRACT_STATE_USED_AS_NEXT_STATE, "s(e)->s"))));
      }

      @Test
      public void concreteStatesCantHaveNullEvents() throws Exception {
        List<AnalysisError> errors = produceAst("{(as) - - - s - - -}").errors;
        assertThat(errors, not(hasItems(new AnalysisError(CONCRETE_STATE_WITH_NO_EVENT, "as"))));
        assertThat(errors, hasItems(new AnalysisError(CONCRETE_STATE_WITH_NO_EVENT, "s")));
      }

      @Test
      public void concreteStatesCantHaveNullNextStates() throws Exception {
        List<AnalysisError> errors = produceAst("{(as) e - - s e - -}").errors;
        assertThat(errors, not(hasItems(new AnalysisError(CONCRETE_STATE_WITH_NO_NEXT_STATE, "as"))));
        assertThat(errors, hasItems(new AnalysisError(CONCRETE_STATE_WITH_NO_NEXT_STATE, "s")));
      }
    } // Transition Errors
  }// Semantic Errors.

  public class Warnings {
    @Test
    public void warnIfStateUsedAsBothAbstractAndConcrete() throws Exception {
      List<AnalysisError> errors = produceAst("{(ias) e - - ias e - - (cas) e - -}").warnings;
      assertThat(errors, not(hasItems(new AnalysisError(INCONSISTENT_ABSTRACTION, "cas"))));
      assertThat(errors, hasItems(new AnalysisError(INCONSISTENT_ABSTRACTION, "ias")));
    }

    @Test
    public void entryAndExitActionsNotDisorganized() throws Exception {
      List<AnalysisError> errors = produceAst(
        "" +
          "{" +
          "  s - - - " +
          "  s - - -" +
          "  es <x - - - " +
          "  es <x - - -" +
          "  xs >x - - -" +
          "  xs >{x} - - -" +
          "}").warnings;
      assertThat(errors, not(hasItems(new AnalysisError(STATE_ACTIONS_DISORGANIZED, "s"))));
      assertThat(errors, not(hasItems(new AnalysisError(STATE_ACTIONS_DISORGANIZED, "es"))));
      assertThat(errors, not(hasItems(new AnalysisError(STATE_ACTIONS_DISORGANIZED, "xs"))));
    }

    @Test
    public void warnIfStateHasMultipleEntryActionDefinitions() throws Exception {
      List<AnalysisError> errors = produceAst("{s - - - ds <x - - - ds <y - -}").warnings;
      assertThat(errors, not(hasItems(new AnalysisError(STATE_ACTIONS_DISORGANIZED, "s"))));
      assertThat(errors, hasItems(new AnalysisError(STATE_ACTIONS_DISORGANIZED, "ds")));
    }

    @Test
    public void warnIfStateHasMultipleExitActionDefinitions() throws Exception {
      List<AnalysisError> errors = produceAst("{ds >x - - - ds >y - -}").warnings;
      assertThat(errors, hasItems(new AnalysisError(STATE_ACTIONS_DISORGANIZED, "ds")));
    }

    @Test
    public void warnIfStateHasDisorganizedEntryAndExitActions() throws Exception {
      List<AnalysisError> errors = produceAst("{ds >x - - - ds <y - -}").warnings;
      assertThat(errors, hasItems(new AnalysisError(STATE_ACTIONS_DISORGANIZED, "ds")));
    }
  }

  public class Lists {
    @Test
    public void oneState() throws Exception {
      AbstractSyntaxTree ast = produceAst("{s - - -}");
      assertThat(ast.states.values(), contains(new State("s")));
    }

    @Test
    public void manyStates() throws Exception {
      AbstractSyntaxTree ast = produceAst("{s1 - - - s2 - - - s3 - - -}");
      assertThat(ast.states.values(), hasItems(
        new State("s1"),
        new State("s2"),
        new State("s3")));
    }

    @Test
    public void statesAreKeyedByName() throws Exception {
      AbstractSyntaxTree ast = produceAst("{s1 - - - s2 - - - s3 - - -}");
      assertThat(ast.states.get("s1"), equalTo(new State("s1")));
      assertThat(ast.states.get("s2"), equalTo(new State("s2")));
      assertThat(ast.states.get("s3"), equalTo(new State("s3")));
    }

    @Test
    public void manyEvents() throws Exception {
      AbstractSyntaxTree ast = produceAst("{s1 e1 - - s2 e2 - - s3 e3 - -}");
      assertThat(ast.events, hasItems("e1", "e2", "e3"));
      assertThat(ast.events, hasSize(3));
    }

    @Test
    public void manyEventsButNoDuplicates() throws Exception {
      AbstractSyntaxTree ast = produceAst("{s1 e1 - - s2 e2 - - s3 e1 - -}");
      assertThat(ast.events, hasItems("e1", "e2"));
      assertThat(ast.events, hasSize(2));
    }

    @Test
    public void manyActionsButNoDuplicates() throws Exception {
      AbstractSyntaxTree ast = produceAst("{s1 e1 - {a1 a2} s2 e2 - {a3 a1}}");
      assertThat(ast.actions, hasItems("a1", "a2", "a3"));
      assertThat(ast.actions, hasSize(3));
    }
  }
}
