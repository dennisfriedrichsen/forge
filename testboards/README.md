# Test boards

Board setups for manual testing, in Forge's puzzle (`.pzl`) format. Launch one straight into a game with:

```bash
tools/board.sh testboards/savannah-birds.pzl
```

The `[state]` section sets each player's life, hand, library, battlefield, graveyard and exile, plus
`turn`, `activeplayer` and `activephase`. See the puzzles in `forge-gui/res/puzzle/` for more keys.
