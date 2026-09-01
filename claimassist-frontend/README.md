# ClaimAssist Frontend

Modern React + TypeScript frontend for the ClaimAssist insurance platform.

## Tech Stack

- **Framework**: React 19 with TypeScript
- **Build Tool**: Vite 8
- **Routing**: React Router DOM
- **Styling**: CSS custom properties (design tokens)
- **Linting**: oxlint

## Getting Started

### Prerequisites

- Node.js 18+ 
- npm or yarn

### Installation

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Build for production
npm run build

# Run linter
npm run lint

# Preview production build
npm run preview
```

### Environment Configuration

Create a `.env` file based on `.env.example`:

```bash
cp .env.example .env
```

Configure the API base URL for your environment:

```env
VITE_API_BASE_URL=http://localhost:8080
```

## Project Structure

```
claimassist-frontend/
├── src/
│   ├── app/              # App-level configurations
│   ├── assets/           # Static assets
│   ├── components/       # Reusable components
│   │   ├── ui/          # Base UI components (Button, Input, etc.)
│   │   ├── layout/      # Layout components (Header, Footer, etc.)
│   │   ├── navigation/  # Navigation components
│   │   └── feedback/    # Feedback components (Toast, etc.)
│   ├── features/        # Feature-specific modules
│   │   ├── auth/        # Authentication
│   │   ├── customer/    # Customer features
│   │   ├── policies/    # Policy management
│   │   ├── claims/      # Claims management
│   │   ├── documents/   # Document handling
│   │   ├── ai/          # AI features
│   │   ├── operations/  # Operations dashboard
│   │   └── admin/       # Admin features
│   ├── hooks/           # Custom React hooks
│   ├── lib/             # Utility libraries
│   ├── services/        # API services
│   ├── types/           # TypeScript type definitions
│   ├── config/          # Configuration files
│   ├── routes/          # Route definitions
│   └── styles/          # Global styles and design tokens
├── public/              # Public assets
└── package.json
```

## Design System

The frontend uses a custom design system with CSS custom properties defined in `src/styles/design-tokens.css`:

- **Colors**: Semantic color palette (primary, secondary, success, warning, danger, info)
- **Typography**: Font sizes, weights, and line heights
- **Spacing**: Consistent spacing scale
- **Shadows**: Elevation levels
- **Border Radius**: Rounded corner variations
- **Breakpoints**: Responsive breakpoints (sm, md, lg, xl)

## Architecture

### Component Architecture

- **UI Components**: Reusable, stateless components in `components/ui/`
- **Layout Components**: Page layout wrappers in `components/layout/`
- **Feature Components**: Business logic in `features/` directories

### API Client

The API client (`services/api-client.ts`) provides:
- Centralized HTTP methods (GET, POST, PUT, PATCH, DELETE)
- Automatic token handling (when implemented)
- Error normalization
- Timeout handling
- SSE streaming support for AI features

### Routing

Routes are defined in `src/routes/index.tsx` using React Router:
- Public routes: `/`, `/products`, `/claims`, `/about`, `/contact`, `/login`, `/register`
- Customer routes: `/dashboard`, `/policies`, `/claims`, `/documents`, `/notifications`, `/profile`
- Operations routes: `/operations`, `/operations/claims`
- Admin routes: `/admin`, `/admin/users`, `/admin/rbac`, `/admin/products`, `/admin/ai`, `/admin/risk`, `/admin/audit`

## Accessibility

The frontend is built with accessibility in mind:
- Semantic HTML elements
- ARIA attributes where appropriate
- Keyboard navigation support
- Focus management
- Screen reader support
- Color contrast compliance
- Reduced motion support

## Responsive Design

Mobile-first responsive design with breakpoints:
- Mobile: < 640px
- Tablet: 640px - 1024px
- Desktop: 1024px - 1280px
- Large Desktop: > 1280px

## Backend Integration

The frontend integrates with the ClaimAssist backend through the API Gateway. See `FRONTEND_BACKEND_MAP.md` for detailed API documentation.

## Development Notes

- This is Phase 1A foundation - pages are placeholders
- Authentication will be implemented in Phase 1B
- All page implementations will follow in subsequent phases
- The design system is inspired by modern insurance UX (like Digit Insurance) but with original branding

## License

Part of the ClaimAssist project.
