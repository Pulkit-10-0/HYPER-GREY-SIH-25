# AyuSure Frontend Dashboard

A modern React-based dashboard built with Next.js for the AyuSure herbal quality analysis system. This frontend provides a comprehensive interface for managing devices, monitoring analysis results, and generating reports.

## Overview

The AyuSure frontend is a responsive web application that connects to the AyuSure backend API to provide real-time monitoring and management capabilities for herbal quality analysis. It features device management, data visualization, report generation, and real-time updates through WebSocket connections.

## Technology Stack

- **Framework**: Next.js 14 with App Router
- **UI Library**: React 18 with TypeScript
- **Styling**: Tailwind CSS with custom components
- **State Management**: Zustand for global state
- **Data Fetching**: TanStack Query (React Query) for server state
- **Real-time**: Socket.IO client for WebSocket connections
- **Charts**: Chart.js with react-chartjs-2 for data visualization
- **Forms**: React Hook Form with Zod validation
- **Authentication**: JWT token management with automatic refresh
- **Icons**: Lucide React for consistent iconography
- **Notifications**: React Hot Toast for user feedback

## Features

### Authentication & User Management
- Secure login with JWT token handling
- Automatic token refresh mechanism
- Role-based UI rendering (Admin, Manager, Operator, Viewer)
- User profile management
- Organization-specific data access

### Device Management
- Device registration and configuration
- Real-time device status monitoring
- Device location mapping with interactive maps
- Calibration management and history tracking
- Device performance metrics and statistics

### Data Visualization
- Real-time sensor data charts and graphs
- Analysis result visualization with quality metrics
- Historical data trends and comparisons
- Interactive dashboards with filtering options
- Export capabilities for charts and data

### Analysis Results
- Comprehensive analysis result display
- Quality grading with visual indicators
- Authenticity verification results
- Contamination detection alerts
- AI-generated recommendations

### Report Generation
- Multi-format report generation (PDF, CSV, JSON)
- Customizable report templates
- Date range selection and filtering
- Automated report scheduling
- Report download and sharing capabilities

### Real-time Updates
- Live device status updates
- Real-time analysis completion notifications
- System alerts and warnings
- WebSocket connection management
- Automatic reconnection handling

## Project Structure

```
frontend/
├── src/
│   ├── app/                    # Next.js App Router pages
│   │   ├── (auth)/            # Authentication pages
│   │   │   ├── login/
│   │   │   └── register/
│   │   ├── dashboard/         # Main dashboard pages
│   │   │   ├── devices/
│   │   │   ├── analysis/
│   │   │   ├── reports/
│   │   │   └── settings/
│   │   ├── layout.tsx         # Root layout component
│   │   ├── page.tsx          # Home page
│   │   └── globals.css       # Global styles
│   ├── components/            # Reusable UI components
│   │   ├── ui/               # Base UI components
│   │   │   ├── button.tsx
│   │   │   ├── input.tsx
│   │   │   ├── modal.tsx
│   │   │   └── table.tsx
│   │   ├── charts/           # Chart components
│   │   │   ├── line-chart.tsx
│   │   │   ├── bar-chart.tsx
│   │   │   └── pie-chart.tsx
│   │   ├── forms/            # Form components
│   │   │   ├── device-form.tsx
│   │   │   ├── login-form.tsx
│   │   │   └── report-form.tsx
│   │   └── layout/           # Layout components
│   │       ├── header.tsx
│   │       ├── sidebar.tsx
│   │       └── footer.tsx
│   ├── lib/                   # Utility libraries and configurations
│   │   ├── api.ts            # API client configuration
│   │   ├── auth.ts           # Authentication utilities
│   │   ├── socket.ts         # WebSocket client setup
│   │   ├── utils.ts          # General utility functions
│   │   └── validations.ts    # Form validation schemas
│   ├── hooks/                 # Custom React hooks
│   │   ├── use-auth.ts       # Authentication hook
│   │   ├── use-devices.ts    # Device management hook
│   │   ├── use-analysis.ts   # Analysis data hook
│   │   └── use-socket.ts     # WebSocket hook
│   ├── stores/                # Zustand state stores
│   │   ├── auth-store.ts     # Authentication state
│   │   ├── device-store.ts   # Device state
│   │   └── ui-store.ts       # UI state
│   ├── types/                 # TypeScript type definitions
│   │   ├── api.ts            # API response types
│   │   ├── auth.ts           # Authentication types
│   │   ├── device.ts         # Device types
│   │   └── analysis.ts       # Analysis types
│   └── constants/             # Application constants
│       ├── api-endpoints.ts  # API endpoint definitions
│       ├── routes.ts         # Application routes
│       └── config.ts         # Configuration constants
├── public/                    # Static assets
│   ├── images/               # Image assets
│   ├── icons/                # Icon files
│   └── favicon.ico          # Favicon
├── package.json              # Dependencies and scripts
├── next.config.js           # Next.js configuration
├── tailwind.config.js       # Tailwind CSS configuration
├── tsconfig.json            # TypeScript configuration
└── README.md                # This file
```

## Getting Started

### Prerequisites
- Node.js 18.0 or higher
- npm or yarn package manager
- AyuSure backend API running (see backend README)

### Installation

1. **Navigate to frontend directory**:
   ```bash
   cd frontend
   ```

2. **Install dependencies**:
   ```bash
   npm install
   # or
   yarn install
   ```

3. **Configure environment variables**:
   ```bash
   cp .env.example .env.local
   # Edit .env.local with your configuration
   ```

4. **Start development server**:
   ```bash
   npm run dev
   # or
   yarn dev
   ```

5. **Open in browser**:
   Navigate to `http://localhost:3000`

### Environment Variables

Create a `.env.local` file with the following variables:

```bash
# API Configuration
NEXT_PUBLIC_API_URL=http://localhost:5000
NEXT_PUBLIC_WS_URL=ws://localhost:5000

# Authentication
NEXT_PUBLIC_JWT_SECRET=your-jwt-secret

# Application
NEXT_PUBLIC_APP_NAME=AyuSure Dashboard
NEXT_PUBLIC_APP_VERSION=1.0.0

# Features
NEXT_PUBLIC_ENABLE_ANALYTICS=true
NEXT_PUBLIC_ENABLE_NOTIFICATIONS=true
```

## Development

### Available Scripts

```bash
# Development server
npm run dev

# Production build
npm run build

# Start production server
npm run start

# Run linting
npm run lint

# Run type checking
npm run type-check

# Run tests
npm run test

# Run tests with coverage
npm run test:coverage
```

### Code Quality

The project uses several tools to maintain code quality:

- **ESLint**: Code linting with Next.js recommended rules
- **Prettier**: Code formatting with consistent style
- **TypeScript**: Static type checking
- **Husky**: Git hooks for pre-commit checks
- **lint-staged**: Run linters on staged files

### Development Guidelines

1. **Component Structure**: Use functional components with TypeScript
2. **State Management**: Use Zustand for global state, React Query for server state
3. **Styling**: Use Tailwind CSS with custom component classes
4. **API Integration**: Use React Query for data fetching and caching
5. **Error Handling**: Implement comprehensive error boundaries
6. **Testing**: Write unit tests for components and integration tests for features

## Key Components

### Authentication System
- JWT token management with automatic refresh
- Protected routes with role-based access
- Login/logout functionality with proper state management
- Session persistence across browser refreshes

### Device Management Interface
- Device registration form with validation
- Device list with filtering and sorting
- Device detail views with real-time status
- Calibration management interface
- Device location mapping

### Data Visualization
- Real-time charts for sensor data
- Analysis result visualization
- Historical data trends
- Interactive dashboards
- Export functionality

### Report Generation
- Report configuration interface
- Preview functionality
- Download management
- Report history and status tracking

### Real-time Features
- WebSocket connection management
- Live data updates
- Real-time notifications
- Connection status indicators
- Automatic reconnection

## API Integration

The frontend integrates with the AyuSure backend API through:

### HTTP Client
- Axios-based API client with interceptors
- Automatic token attachment
- Request/response logging
- Error handling and retry logic

### WebSocket Client
- Socket.IO client for real-time communication
- Event-based message handling
- Connection state management
- Automatic reconnection logic

### Data Management
- React Query for server state management
- Optimistic updates for better UX
- Background data synchronization
- Cache invalidation strategies

## Styling and Theming

### Tailwind CSS Configuration
- Custom color palette matching brand guidelines
- Responsive design utilities
- Dark mode support
- Custom component classes

### Component Library
- Reusable UI components with consistent styling
- Accessible components following WCAG guidelines
- Responsive design patterns
- Loading states and error handling

## Testing

### Testing Strategy
- Unit tests for individual components
- Integration tests for feature workflows
- End-to-end tests for critical user journeys
- API integration tests

### Testing Tools
- Jest for unit testing
- React Testing Library for component testing
- Cypress for end-to-end testing
- MSW for API mocking

## Performance Optimization

### Next.js Features
- Server-side rendering for initial page loads
- Static generation for public pages
- Image optimization with Next.js Image component
- Code splitting and lazy loading

### React Optimization
- Component memoization with React.memo
- Callback memoization with useCallback
- Effect optimization with useEffect dependencies
- Virtual scrolling for large lists

### Bundle Optimization
- Tree shaking for unused code elimination
- Dynamic imports for code splitting
- Bundle analysis with webpack-bundle-analyzer
- Compression and minification

## Deployment

### Development Deployment
```bash
npm run build
npm run start
```

### Production Deployment

#### Using Docker
```bash
# Build Docker image
docker build -t ayusure-frontend .

# Run container
docker run -p 3000:3000 ayusure-frontend
```

#### Using Vercel
```bash
# Install Vercel CLI
npm i -g vercel

# Deploy to Vercel
vercel --prod
```

#### Using Netlify
```bash
# Build for production
npm run build

# Deploy to Netlify
# Upload the 'out' directory to Netlify
```

### Environment-Specific Configuration
- Development: Hot reloading, debug tools enabled
- Staging: Production build with staging API endpoints
- Production: Optimized build with production API endpoints

## Browser Support

The application supports modern browsers:
- Chrome 90+
- Firefox 88+
- Safari 14+
- Edge 90+

## Accessibility

The application follows accessibility best practices:
- WCAG 2.1 AA compliance
- Keyboard navigation support
- Screen reader compatibility
- High contrast mode support
- Focus management

## Security

### Client-Side Security
- XSS protection through proper data sanitization
- CSRF protection with token validation
- Secure token storage in httpOnly cookies
- Content Security Policy headers

### Data Protection
- Sensitive data encryption in transit
- No sensitive data in localStorage
- Proper error message handling
- Input validation and sanitization

## Troubleshooting

### Common Issues

1. **API Connection Failed**
   - Check backend server status
   - Verify API URL in environment variables
   - Check CORS configuration

2. **WebSocket Connection Issues**
   - Verify WebSocket URL configuration
   - Check firewall and proxy settings
   - Monitor connection status in browser dev tools

3. **Authentication Problems**
   - Clear browser cache and cookies
   - Check JWT token expiration
   - Verify backend authentication endpoints

4. **Build Failures**
   - Clear node_modules and reinstall dependencies
   - Check TypeScript errors
   - Verify environment variable configuration

### Performance Issues
- Monitor bundle size with webpack-bundle-analyzer
- Check for memory leaks in React components
- Optimize image sizes and formats
- Review API call patterns and caching

