package forge.gamemodes.match.input;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;

import java.util.List;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.card.mana.ManaCost;
import forge.card.mana.ManaCostParser;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/**
 * Clicking a land with several mana abilities while paying a cost should only ask which ability to use when the
 * answer isn't obvious (issue #8494: tapping Savannah to pay {G} shouldn't offer {W}).
 */
public class ObviousManaAbilityTest extends AITest {

    private SpellAbility obvious(final Game game, final Card source, final Card spell, final String cost) {
        final Player p = source.getController();
        final List<SpellAbility> abilities = InputPayMana.getAllManaAbilities(source, p);
        final SpellAbility saPaidFor = spell.getFirstSpellAbility();
        saPaidFor.setActivatingPlayer(p);
        final ManaCostBeingPaid manaCost = new ManaCostBeingPaid(new ManaCost(new ManaCostParser(cost)));
        return InputPayMana.findObviousManaAbility(abilities, manaCost, saPaidFor, p);
    }

    @Test
    public void dualLandPicksTheOnlyNeededColor() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        final Card savannah = addCard("Savannah", p);
        final Card birds = addCardToZone("Birds of Paradise", p, ZoneType.Hand);

        final SpellAbility chosen = obvious(game, savannah, birds, "G");
        assertNotNull(chosen, "only Savannah's {G} ability can pay {G}");
        assertEquals(chosen.getManaPart().getOrigProduced(), "G");
    }

    @Test
    public void dualLandPicksTheColoredPartOfAMixedCost() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        final Card savannah = addCard("Savannah", p);
        final Card spell = addCardToZone("Grizzly Bears", p, ZoneType.Hand);

        final SpellAbility chosen = obvious(game, savannah, spell, "1 G");
        assertNotNull(chosen);
        assertEquals(chosen.getManaPart().getOrigProduced(), "G");
    }

    @Test
    public void asksWhenBothColorsAreNeeded() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        final Card savannah = addCard("Savannah", p);
        final Card spell = addCardToZone("Grizzly Bears", p, ZoneType.Hand);

        assertNull(obvious(game, savannah, spell, "G W"));
    }

    @Test
    public void asksWhenOnlyGenericIsLeft() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        final Card savannah = addCard("Savannah", p);
        final Card spell = addCardToZone("Grizzly Bears", p, ZoneType.Hand);

        assertNull(obvious(game, savannah, spell, "1"));
    }

    @Test
    public void asksWhenTheSpellCountsColorsSpent() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        final Card savannah = addCard("Savannah", p);
        final Card converge = addCardToZone("Radiant Flames", p, ZoneType.Hand);

        assertNull(obvious(game, savannah, converge, "G"));
    }

    @Test
    public void asksWhenTheAbilityHasASideEffect() {
        final Game game = initAndCreateGame();
        final Player p = game.getPlayers().get(1);
        final Card wastes = addCard("Adarkar Wastes", p);
        final Card spell = addCardToZone("Savannah Lions", p, ZoneType.Hand);

        assertNull(obvious(game, wastes, spell, "W"), "the {W} ability also deals damage, so don't pick it silently");
    }
}
