---
name: Anytime Spanish
colors:
  surface: '#fff8f6'
  surface-dim: '#e5d7d4'
  surface-bright: '#fff8f6'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#fff0ee'
  surface-container: '#f9ebe8'
  surface-container-high: '#f3e5e2'
  surface-container-highest: '#eedfdd'
  on-surface: '#211a19'
  on-surface-variant: '#534340'
  inverse-surface: '#372f2d'
  inverse-on-surface: '#fceeeb'
  outline: '#86736f'
  outline-variant: '#d9c1bd'
  surface-tint: '#8f4b3e'
  primary: '#8f4b3e'
  on-primary: '#ffffff'
  primary-container: '#dc8a7a'
  on-primary-container: '#5e251a'
  inverse-primary: '#ffb4a5'
  secondary: '#765a26'
  on-secondary: '#ffffff'
  secondary-container: '#fed797'
  on-secondary-container: '#785c28'
  tertiary: '#735948'
  on-tertiary: '#ffffff'
  tertiary-container: '#b89986'
  on-tertiary-container: '#473223'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#ffdad3'
  primary-fixed-dim: '#ffb4a5'
  on-primary-fixed: '#3a0a04'
  on-primary-fixed-variant: '#723429'
  secondary-fixed: '#ffdea9'
  secondary-fixed-dim: '#e6c183'
  on-secondary-fixed: '#271900'
  on-secondary-fixed-variant: '#5c4210'
  tertiary-fixed: '#ffdcc6'
  tertiary-fixed-dim: '#e1c0ab'
  on-tertiary-fixed: '#29170a'
  on-tertiary-fixed-variant: '#594232'
  background: '#fff8f6'
  on-background: '#211a19'
  surface-variant: '#eedfdd'
typography:
  display-lg:
    fontFamily: Libre Caslon Text
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: -0.01em
  display-lg-mobile:
    fontFamily: Libre Caslon Text
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 38px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Libre Caslon Text
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  body-lg:
    fontFamily: DM Sans
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 30px
  body-md:
    fontFamily: DM Sans
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 26px
  label-sm:
    fontFamily: DM Sans
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.05em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: '8'
  margin-mobile: '24'
  gutter: '16'
  section-gap: '40'
---

## Brand & Style

The design system is built on the concept of a **"Digital Traveler's Journal."** It eschews the clinical, hyper-efficient aesthetic of modern SaaS in favor of a warm, organic, and tactile experience. The goal is to lower the "affective filter" for language learners, making the act of speaking Spanish feel like a relaxed conversation in a sun-drenched plaza rather than a classroom drill.

The style leans into a **refined organic minimalism**. It utilizes soft textures, generous negative space, and hand-drawn influences. Motion should be fluid and rhythmic—mimicking the natural cadence of speech—rather than mechanical or snappy.

## Colors

The palette is inspired by Mediterranean and Latin American landscapes—earthy, sun-baked, and sophisticated.

- **Primary (Sun-Bleached Terracotta):** A lightened, more vibrant clay tone (#dc8a7a). Used for key structural elements, primary branding, and progress indicators. It conveys warmth, history, and a welcoming openness.
- **Secondary (Soft Amber):** A lightened, creamy saffron tone (#f3cd8e). Reserved exclusively for the "Moment of Action"—the microphone button and active voice states. It represents energy, clarity, and the spark of communication.
- **Neutral/Text (Warm Dark Brown):** Replaces harsh blacks to maintain a soft, ink-on-paper contrast level (#3a2618).
- **Background (Dusty Sand):** Provides a warm, non-reflective canvas that reduces eye strain and feels more like parchment than a screen.

## Typography

The typography strategy balances the classic, literary elegance of **Libre Caslon Text** for headers with the clear, modern legibility of **DM Sans** for instructional text. The use of a serif typeface for headlines reinforces the "journal" aesthetic, suggesting a printed travelogue or a well-loved book of poetry.

To maintain the "phrasebook" feel, line heights are intentionally loose. This creates a sense of "breath" between lines of dialogue. Spanish text (the target language) should often be styled in the Primary Terracotta color or a slightly heavier weight to distinguish it visually from English translations.

## Layout & Spacing

This design system uses a **Fluid Center** layout model. On mobile devices, side margins are generous (24px) to ensure the thumb naturally rests on the center-weighted microphone UI.

The spacing rhythm is "loose." Avoid packing elements tightly. Every interaction point should be surrounded by "air" to reflect a calm, unhurried learning pace. Vertical stacks should favor larger gaps (16px to 24px) over tight lists.

## Elevation & Depth

In keeping with the organic theme, this design system avoids heavy shadows. Depth is communicated through **Tonal Layering** and **Soft Insets**.

- **Surfaces:** Cards and containers use a slightly lighter version of the background color or a subtle 1px border in a muted terracotta tint.
- **The Voice Layer:** When the microphone is active, use a soft background blur (glassmorphism) with a warm tint to pull the focus forward, dimming the "journal" content behind it.
- **No Hard Dropshadows:** If elevation is required, use "Ambient Glows"—very wide, low-opacity shadows (4-8% opacity) that match the color of the object casting them.

## Shapes

The shape language is defined by **High Circularity**. There are no sharp corners in this design system.

- **Standard Containers:** Use 1rem (rounded-lg) for content cards.
- **Buttons & Inputs:** Use 1.5rem (rounded-xl) or full-pill shapes to make them feel "touchable" and soft.
- **Icons:** Use "hand-adjacent" line icons with rounded caps and corners. Avoid perfectly straight geometric lines; a subtle "imperfect" curve is preferred to reinforce the artisanal feel.

## Components

### The Pulse (Voice UI)
The central component. Instead of a linear bar, use a fluid, organic "blob" or waveform that expands and contracts. It should feel like a living thing, colored in the Soft Amber accent.

### Primary Action Button
Large, pill-shaped, and colored in Soft Amber. When inactive or "listening," the button can transform into the aforementioned organic waveform.

### Phrase Cards
Used for displaying Spanish/English pairs. They should have a subtle 1px stroke (Primary Terracotta at 10% opacity) and generous internal padding. The Spanish text is always the hero.

### Audio Progress
Avoid "techy" sliders. Use a soft, thick line with a rounded head. The "track" should be a darker shade of the background color, not grey.

### Feedback Toasts
Use soft, rounded banners at the top of the screen. Success states use a warm sage green (earthy, not neon), and errors use a muted brick red.