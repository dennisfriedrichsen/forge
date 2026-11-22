package forge.gamemodes.match.input;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * Paying only generic mana with an "any color" source (e.g. Birds of Paradise for Sol Ring) shouldn't ask which
 * color to make, unless the color could still matter.
 */
public class AnyColorForGenericTest extends AITest {

    private boolean safe(final Card source, final Card spell) {
        final Player p = source.getController();
        // read the ability off the card: summoning sickness doesn't matter for this check
        final SpellAbility ma = source.getManaAbilities().get(0);
        ma.setActivatingPlayer(p);
        final SpellAbility saPaidFor = spell.getFirstSpellAbility();
        saPaidFor.setActivatingPlayer(p);
        return InputPayMana.isAnyColorForGenericSafe(ma, saPaidFor);
    }

    @Test
    public void birdsPayingSolRingDoesNotAsk() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        final Card birds = addCard("Birds of Paradise", p);
        final Card solRing = addCardToZone("Sol Ring", p, ZoneType.Hand);

        assertTrue(safe(birds, solRing));
    }

    @Test
    public void asksWhenTheSpellCountsColorsSpent() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        final Card birds = addCard("Birds of Paradise", p);

        assertFalse(safe(birds, addCardToZone("Radiant Flames", p, ZoneType.Hand)), "Converge");
        assertFalse(safe(birds, addCardToZone("Etched Oracle", p, ZoneType.Hand)), "Sunburst");
    }

    @Test
    public void asksWhenTheRestWouldFloat() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        final Card lotus = addCard("Gilded Lotus", p);
        final Card solRing = addCardToZone("Sol Ring", p, ZoneType.Hand);

        assertFalse(safe(lotus, solRing), "three mana of one color: the leftover two would float");
    }

    @Test
    public void asksWhenTheAbilityHasASideEffect() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        final Card ring = addCard("Mana Confluence", p);
        final Card solRing = addCardToZone("Sol Ring", p, ZoneType.Hand);

        assertFalse(safe(ring, solRing), "Mana Confluence costs life, so don't activate it silently");
    }
}
