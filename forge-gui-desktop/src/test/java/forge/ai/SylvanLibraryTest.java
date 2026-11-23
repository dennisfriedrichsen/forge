package forge.ai;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.testng.annotations.Test;

import com.google.common.collect.Iterables;

import forge.game.Game;
import forge.game.ability.AbilityUtils;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardCollectionView;
import forge.game.cost.Cost;
import forge.game.cost.CostPart;
import forge.game.cost.CostPayLife;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.collect.FCollectionView;

/**
 * Sylvan Library asks once which drawn cards to put back. If that keeps any of the two chosen cards, it asks once
 * whether to pay 4 life for each, defaulting to No; declining goes back to the choice.
 */
public class SylvanLibraryTest extends AITest {

    /**
     * Puts back the first {@code picks[round]} drawn cards each round and answers payment prompts from {@code confirms}.
     * AI cost payment never asks, so the unless-cost is paid here the way the human client does it.
     */
    private static class Scripted extends PlayerControllerAi {
        private final int[] picks;
        private final boolean[] confirms;
        int rounds;
        final List<String> questions = new ArrayList<>();
        boolean defaultNo = true;
        int orderPrompts;

        Scripted(final Game game, final Player p, final int[] picks, final boolean[] confirms) {
            super(game, p, p.getLobbyPlayer());
            this.picks = picks;
            this.confirms = confirms;
        }

        @Override
        public CardCollectionView chooseCardsForEffect(final CardCollectionView sourceList, final SpellAbility sa, final String title,
                final int min, final int max, final boolean isOptional, final Map<String, Object> params) {
            if (sa.getApi() == ApiType.ChooseCard) {
                assertEquals(min, 0, "putting cards back is up to the player");
                return new CardCollection(Iterables.limit(sourceList, picks[rounds++]));
            }
            return super.chooseCardsForEffect(sourceList, sa, title, min, max, isOptional, params);
        }

        @Override
        public boolean payCostToPreventEffect(final Cost cost, final SpellAbility sa, final boolean alreadyPaid,
                final FCollectionView<Player> allPayers) {
            // like the human path: unpayable means no prompt; otherwise ask, then pay
            final CostPayLife part = (CostPayLife) Iterables.getOnlyElement(cost.getCostParts());
            final int amount = part.getAbilityAmount(sa);
            if (!getPlayer().canPayLife(amount, true, sa) || !confirmPayment(part, "Pay " + amount + " life?", sa)) {
                return false;
            }
            return getPlayer().payLife(amount, sa, true);
        }

        @Override
        public CardCollectionView orderMoveToZoneList(final CardCollectionView cards, final ZoneType destinationZone,
                final SpellAbility source) {
            orderPrompts++;
            return cards;
        }

        @Override
        public boolean confirmPayment(final CostPart costPart, final String question, final SpellAbility sa) {
            defaultNo &= sa.hasParam("UnlessDefaultNo");
            questions.add(question);
            return confirms[questions.size() - 1];
        }
    }

    private Scripted resolve(final Game game, final Player p, final int life, final int[] picks, final boolean... confirms) {
        final Card library = addCard("Sylvan Library", p);
        fillLibrary(p, 10);
        p.setLife(life, null);
        p.drawCards(3); // the normal draw plus the two extra

        final SpellAbility sa = library.getTriggers().getFirst().ensureAbility();
        sa.setActivatingPlayer(p);
        final Scripted controller = new Scripted(game, p, picks, confirms);
        p.runWithController(() -> AbilityUtils.resolve(sa), controller);
        assertTrue(library.getChosenCards().isEmpty(), "chosen cards are cleaned up");
        assertEquals(controller.rounds, picks.length, "number of times the choice was asked");
        assertEquals(controller.questions.size(), confirms.length, "number of payment prompts");
        assertTrue(controller.defaultNo, "the payment prompt defaults to No");
        return controller;
    }

    @Test
    public void puttingTwoBackCostsNoLifeAndAsksNothing() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        final Scripted c = resolve(game, p, 20, new int[] {2});

        assertEquals(p.getLife(), 20);
        assertEquals(p.getCardsIn(ZoneType.Hand).size(), 1);
        assertEquals(p.getCardsIn(ZoneType.Library).size(), 9);
        assertEquals(c.orderPrompts, 1, "two cards go on top, so ask which goes first");
    }

    @Test
    public void keepingOneAsksToPay4() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        final Scripted c = resolve(game, p, 20, new int[] {1}, true);

        assertTrue(c.questions.get(0).contains("4"), c.questions.get(0));
        assertEquals(p.getLife(), 16);
        assertEquals(p.getCardsIn(ZoneType.Hand).size(), 2);
        assertEquals(c.orderPrompts, 0, "one card going back has no order to choose");
    }

    @Test
    public void keepingAllAsksOnceToPay8() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        final Scripted c = resolve(game, p, 20, new int[] {0}, true);

        assertTrue(c.questions.get(0).contains("8"), c.questions.get(0));
        assertEquals(p.getLife(), 12, "only two of the three drawn cards are chosen, so pay 4 twice");
        assertEquals(p.getCardsIn(ZoneType.Hand).size(), 3);
        assertEquals(c.orderPrompts, 0);
    }

    @Test
    public void decliningToPayGoesBackToTheChoice() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        final Scripted c = resolve(game, p, 20, new int[] {0, 2}, false);

        assertEquals(p.getLife(), 20, "declining costs nothing");
        assertEquals(p.getCardsIn(ZoneType.Hand).size(), 1);
        assertEquals(p.getCardsIn(ZoneType.Library).size(), 9);
        assertEquals(c.orderPrompts, 1);
    }

    @Test
    public void unaffordableKeepGoesBackToTheChoice() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        resolve(game, p, 5, new int[] {0, 1}, true); // 8 life can't be paid, so no prompt; then keep one for 4

        assertEquals(p.getLife(), 1);
        assertEquals(p.getCardsIn(ZoneType.Hand).size(), 2);
        assertEquals(p.getCardsIn(ZoneType.Library).size(), 8);
    }
}
