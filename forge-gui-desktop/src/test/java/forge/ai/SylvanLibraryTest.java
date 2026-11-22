package forge.ai;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

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
import forge.game.player.PlaySpellAbility;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.util.collect.FCollectionView;

/**
 * Sylvan Library asks once which drawn cards to put back, then pays 4 life for each of the two chosen cards kept,
 * without a separate prompt per card. If a payment isn't possible, a drawn card goes back instead.
 */
public class SylvanLibraryTest extends AITest {

    /** Puts back the first {@code toReturn} drawn cards and pays like a human, through the resolve-time cost code. */
    private static class Scripted extends PlayerControllerAi {
        private final int toReturn;
        boolean askedToConfirmPayment;

        Scripted(final Game game, final Player p, final int toReturn) {
            super(game, p, p.getLobbyPlayer());
            this.toReturn = toReturn;
        }

        @Override
        public CardCollectionView chooseCardsForEffect(final CardCollectionView sourceList, final SpellAbility sa, final String title,
                final int min, final int max, final boolean isOptional, final Map<String, Object> params) {
            if (sa.getApi() == ApiType.ChooseCard) {
                assertEquals(min, 0, "putting cards back is up to the player");
                return new CardCollection(Iterables.limit(sourceList, toReturn));
            }
            return super.chooseCardsForEffect(sourceList, sa, title, min, max, isOptional, params);
        }

        @Override
        public boolean payCostToPreventEffect(final Cost cost, final SpellAbility sa, final boolean alreadyPaid,
                final FCollectionView<Player> allPayers) {
            return PlaySpellAbility.payCostDuringAbilityResolve(this, getPlayer(), cost, sa, null);
        }

        @Override
        public boolean confirmPayment(final CostPart costPart, final String question, final SpellAbility sa) {
            askedToConfirmPayment = true;
            return true;
        }
    }

    private Scripted resolve(final Game game, final Player p, final int life, final int toReturn) {
        final Card library = addCard("Sylvan Library", p);
        fillLibrary(p, 10);
        p.setLife(life, null);
        p.drawCards(3); // the normal draw plus the two extra

        final SpellAbility sa = library.getTriggers().getFirst().ensureAbility();
        sa.setActivatingPlayer(p);
        final Scripted controller = new Scripted(game, p, toReturn);
        p.runWithController(() -> AbilityUtils.resolve(sa), controller);
        assertTrue(library.getChosenCards().isEmpty(), "chosen cards are cleaned up");
        return controller;
    }

    @Test
    public void puttingTwoBackCostsNoLife() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        final Scripted c = resolve(game, p, 20, 2);

        assertEquals(p.getLife(), 20);
        assertEquals(p.getCardsIn(ZoneType.Hand).size(), 1);
        assertEquals(p.getCardsIn(ZoneType.Library).size(), 9);
        assertFalse(c.askedToConfirmPayment);
    }

    @Test
    public void keepingOnePays4LifeWithoutAsking() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        final Scripted c = resolve(game, p, 20, 1);

        assertEquals(p.getLife(), 16);
        assertEquals(p.getCardsIn(ZoneType.Hand).size(), 2);
        assertFalse(c.askedToConfirmPayment, "the keep decision was already made, so don't ask to pay");
    }

    @Test
    public void keepingAllPays8Life() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        final Scripted c = resolve(game, p, 20, 0);

        assertEquals(p.getLife(), 12, "only two of the three drawn cards are chosen, so pay twice");
        assertEquals(p.getCardsIn(ZoneType.Hand).size(), 3);
        assertFalse(c.askedToConfirmPayment);
    }

    @Test
    public void cardGoesBackWhenLifeRunsShort() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        resolve(game, p, 5, 0);

        assertEquals(p.getLife(), 1, "the first payment is possible");
        assertEquals(p.getCardsIn(ZoneType.Hand).size(), 2, "the second can't be paid, so a card goes back");
        assertEquals(p.getCardsIn(ZoneType.Library).size(), 8);
    }
}
