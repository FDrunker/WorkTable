# Public Chat Design

## Creative thesis

The public AI pages should feel like a calm editorial workbench: quick enough for repeated operational use, but visibly shaped by WorkTable rather than assembled from a generic chat component library.

## Principles

- Structure first: navigation, history, messages, and input remain immediately legible.
- Use lines, spacing, and alignment to organize the page before adding color or surfaces.
- Keep one accent color. Mineral green marks action, selection, and evidence.
- Keep message content primarily as writing, not as a stack of cards.
- Preserve a compact desktop density and a focused single-column mobile experience.

## Anti-principles

- No glossy gradients, glass panels, glow, oversized shadows, or decorative blobs.
- No pill navigation, capsule-heavy controls, or excessive 12-20px radii.
- No card wrapping around every message or configuration row.
- No second decorative color competing with mineral green.
- No centered marketing composition on task-heavy screens.

## Typography

- Interface text: Microsoft YaHei / PingFang SC / Noto Sans SC.
- Metadata and indices: Cascadia Mono / SFMono-Regular / Consolas.
- Welcome display text: Songti SC / Noto Serif SC / SimSun.
- Body copy stays between 14px and 15px with generous line height.

## Color

- Ink: `#18211e`
- Muted ink: `#4f5c57`
- Paper: `#f3f5f0`
- Raised paper: `#fbfcf8`
- Rule: `#dce2da`
- Mineral green: `#0b6a57`
- Signal orange: `#ad5b28`, reserved for unavailable/error states

## Composition

- Header: 64px with a centered scene navigator.
- Desktop history rail: 248px.
- Message and composer measure: maximum 840px.
- The history rail uses a numbered index rhythm. The active conversation gets a mineral-green left rule.
- Empty-state content may remain centered because it is a conversational entry point, but surrounding controls use deliberate lines rather than floating cards.

## Shape language

- Default radius: 3-6px.
- Buttons use icon-first controls where the action is familiar.
- Circular forms are reserved for status dots and true icon-only controls.

## Signature artifacts

- Numbered scene tabs with an underline instead of pill selection.
- A mineral-green index rule on the active conversation.
- Editorial serif welcome headings paired with monospace operational metadata.

## Interaction

- Scene changes preserve the page shell and move only the active underline.
- History selection is quiet and immediate; delete actions appear only on hover.
- Composer focus uses a crisp green outline rather than a floating shadow.
- Mobile replaces the history rail with a drawer while retaining the centered scene switcher.

## Accessibility and usability constraints

- Maintain keyboard focus visibility.
- Preserve at least 4.5:1 contrast for body text.
- Keep touch targets at least 36px high.
- Avoid horizontal page overflow at 390px.
