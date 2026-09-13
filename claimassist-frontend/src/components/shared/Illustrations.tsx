interface IllustrationProps {
  className?: string;
}

export function HeroIllustration({ className = '' }: IllustrationProps) {
  return (
    <svg viewBox="0 0 400 320" className={className} fill="none" xmlns="http://www.w3.org/2000/svg">
      {/* Background shield */}
      <path d="M200 20L340 60V170C340 240 280 290 200 310C120 290 60 240 60 170V60L200 20Z" fill="url(#heroGrad)" opacity="0.08" />
      <path d="M200 40L320 72V168C320 228 270 270 200 288C130 270 80 228 80 168V72L200 40Z" fill="url(#heroGrad)" opacity="0.12" />

      {/* Main shield */}
      <path d="M200 60L300 90V165C300 220 260 255 200 270C140 255 100 220 100 165V90L200 60Z" fill="url(#heroShield)" />
      <path d="M200 60L300 90V165C300 220 260 255 200 270C140 255 100 220 100 165V90L200 60Z" stroke="url(#heroStroke)" strokeWidth="2" fill="none" opacity="0.3" />

      {/* Checkmark */}
      <path d="M165 165L190 190L240 140" stroke="white" strokeWidth="6" strokeLinecap="round" strokeLinejoin="round" />

      {/* Floating cards */}
      <rect x="280" y="100" width="80" height="50" rx="8" fill="white" stroke="#E2E8F0" strokeWidth="1" />
      <rect x="290" y="112" width="40" height="6" rx="3" fill="#3B82F6" />
      <rect x="290" y="124" width="60" height="4" rx="2" fill="#CBD5E1" />
      <rect x="290" y="132" width="50" height="4" rx="2" fill="#CBD5E1" />
      <circle cx="345" cy="135" r="8" fill="#10B981" opacity="0.2" />
      <path d="M341 135L344 138L349 132" stroke="#10B981" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />

      <rect x="40" y="180" width="80" height="50" rx="8" fill="white" stroke="#E2E8F0" strokeWidth="1" />
      <rect x="50" y="192" width="30" height="6" rx="3" fill="#3B82F6" />
      <rect x="50" y="204" width="55" height="4" rx="2" fill="#CBD5E1" />
      <rect x="50" y="212" width="45" height="4" rx="2" fill="#CBD5E1" />

      {/* Small dots */}
      <circle cx="330" cy="220" r="4" fill="#3B82F6" opacity="0.3" />
      <circle cx="50" cy="80" r="3" fill="#3B82F6" opacity="0.3" />
      <circle cx="350" cy="60" r="5" fill="#3B82F6" opacity="0.15" />

      <defs>
        <linearGradient id="heroGrad" x1="0" y1="0" x2="400" y2="320" gradientUnits="userSpaceOnUse">
          <stop stopColor="#3B82F6" />
          <stop offset="1" stopColor="#1E40AF" />
        </linearGradient>
        <linearGradient id="heroShield" x1="100" y1="60" x2="300" y2="270" gradientUnits="userSpaceOnUse">
          <stop stopColor="#3B82F6" />
          <stop offset="1" stopColor="#1D4ED8" />
        </linearGradient>
        <linearGradient id="heroStroke" x1="100" y1="60" x2="300" y2="270" gradientUnits="userSpaceOnUse">
          <stop stopColor="#60A5FA" />
          <stop offset="1" stopColor="#2563EB" />
        </linearGradient>
      </defs>
    </svg>
  );
}

export function ClaimsIllustration({ className = '' }: IllustrationProps) {
  return (
    <svg viewBox="0 0 360 280" className={className} fill="none" xmlns="http://www.w3.org/2000/svg">
      {/* Document */}
      <rect x="80" y="40" width="200" height="220" rx="12" fill="white" stroke="#E2E8F0" strokeWidth="1.5" />
      <rect x="80" y="40" width="200" height="40" rx="12" fill="url(#docGrad)" />
      <rect x="80" y="60" width="200" height="20" fill="url(#docGrad)" />
      <rect x="100" y="52" width="60" height="6" rx="3" fill="white" opacity="0.9" />

      {/* Document lines */}
      <rect x="100" y="100" width="160" height="6" rx="3" fill="#3B82F6" />
      <rect x="100" y="118" width="120" height="4" rx="2" fill="#CBD5E1" />
      <rect x="100" y="130" width="140" height="4" rx="2" fill="#CBD5E1" />
      <rect x="100" y="142" width="100" height="4" rx="2" fill="#CBD5E1" />

      {/* Status badge */}
      <rect x="100" y="165" width="80" height="24" rx="12" fill="#DBF4E8" stroke="#10B981" strokeWidth="1" />
      <circle cx="112" cy="177" r="4" fill="#10B981" />
      <rect x="122" y="174" width="50" height="6" rx="3" fill="#059669" />

      {/* Timeline dots */}
      <circle cx="110" cy="210" r="6" fill="#3B82F6" />
      <rect x="120" y="207" width="50" height="6" rx="3" fill="#CBD5E1" />
      <circle cx="190" cy="210" r="6" fill="#3B82F6" opacity="0.5" />
      <rect x="200" y="207" width="40" height="6" rx="3" fill="#E2E8F0" />
      <circle cx="260" cy="210" r="6" fill="#E2E8F0" />

      {/* Floating check */}
      <circle cx="300" cy="80" r="20" fill="#10B981" opacity="0.15" />
      <path d="M292 80L298 86L308 74" stroke="#10B981" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />

      <defs>
        <linearGradient id="docGrad" x1="80" y1="40" x2="280" y2="80" gradientUnits="userSpaceOnUse">
          <stop stopColor="#3B82F6" />
          <stop offset="1" stopColor="#2563EB" />
        </linearGradient>
      </defs>
    </svg>
  );
}

export function AIIllustration({ className = '' }: IllustrationProps) {
  return (
    <svg viewBox="0 0 320 260" className={className} fill="none" xmlns="http://www.w3.org/2000/svg">
      {/* Chat bubble main */}
      <rect x="60" y="40" width="200" height="100" rx="16" fill="url(#aiGrad)" />
      <path d="M100 140L90 165L120 140Z" fill="url(#aiGrad)" />
      <circle cx="90" cy="90" r="12" fill="white" opacity="0.9" />
      <path d="M85 90L89 94L96 86" stroke="url(#aiGrad)" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      <rect x="115" y="75" width="120" height="6" rx="3" fill="white" opacity="0.7" />
      <rect x="115" y="90" width="90" height="6" rx="3" fill="white" opacity="0.5" />
      <rect x="115" y="105" width="110" height="6" rx="3" fill="white" opacity="0.4" />

      {/* Small bubble */}
      <rect x="160" y="170" width="120" height="60" rx="12" fill="white" stroke="#E2E8F0" strokeWidth="1.5" />
      <path d="M180 170L170 155L190 170Z" fill="white" stroke="#E2E8F0" strokeWidth="1.5" />
      <rect x="175" y="185" width="80" height="5" rx="2.5" fill="#3B82F6" opacity="0.6" />
      <rect x="175" y="198" width="60" height="5" rx="2.5" fill="#CBD5E1" />

      {/* Sparkles */}
      <path d="M40 100L42 106L48 108L42 110L40 116L38 110L32 108L38 106Z" fill="#3B82F6" opacity="0.3" />
      <path d="M280 30L282 35L287 37L282 39L280 44L278 39L273 37L278 35Z" fill="#3B82F6" opacity="0.4" />

      <defs>
        <linearGradient id="aiGrad" x1="60" y1="40" x2="260" y2="140" gradientUnits="userSpaceOnUse">
          <stop stopColor="#3B82F6" />
          <stop offset="1" stopColor="#1D4ED8" />
        </linearGradient>
      </defs>
    </svg>
  );
}

export function SupportIllustration({ className = '' }: IllustrationProps) {
  return (
    <svg viewBox="0 0 320 260" className={className} fill="none" xmlns="http://www.w3.org/2000/svg">
      {/* Headset */}
      <path d="M100 100C100 70 130 50 160 50C190 50 220 70 220 100" stroke="url(#supGrad)" strokeWidth="4" strokeLinecap="round" />
      <rect x="85" y="95" width="30" height="50" rx="8" fill="url(#supGrad)" />
      <rect x="205" y="95" width="30" height="50" rx="8" fill="url(#supGrad)" />
      <rect x="90" y="100" width="20" height="40" rx="6" fill="white" opacity="0.2" />
      <rect x="210" y="100" width="20" height="40" rx="6" fill="white" opacity="0.2" />

      {/* Mic */}
      <path d="M220 140C220 170 200 185 160 185" stroke="url(#supGrad)" strokeWidth="3" strokeLinecap="round" fill="none" />
      <circle cx="160" cy="185" r="8" fill="url(#supGrad)" />

      {/* Face dots */}
      <circle cx="145" cy="110" r="3" fill="white" opacity="0.8" />
      <circle cx="175" cy="110" r="3" fill="white" opacity="0.8" />
      <path d="M145 125Q160 135 175 125" stroke="white" strokeWidth="2" strokeLinecap="round" opacity="0.6" fill="none" />

      {/* Floating dots */}
      <circle cx="60" cy="60" r="4" fill="#3B82F6" opacity="0.2" />
      <circle cx="270" cy="80" r="5" fill="#3B82F6" opacity="0.15" />
      <circle cx="250" cy="200" r="3" fill="#3B82F6" opacity="0.25" />

      <defs>
        <linearGradient id="supGrad" x1="85" y1="50" x2="235" y2="185" gradientUnits="userSpaceOnUse">
          <stop stopColor="#3B82F6" />
          <stop offset="1" stopColor="#1D4ED8" />
        </linearGradient>
      </defs>
    </svg>
  );
}

export function EmptyStateIllustration({ className = '' }: IllustrationProps) {
  return (
    <svg viewBox="0 0 200 160" className={className} fill="none" xmlns="http://www.w3.org/2000/svg">
      <rect x="50" y="30" width="100" height="110" rx="10" fill="white" stroke="#E2E8F0" strokeWidth="1.5" />
      <rect x="50" y="30" width="100" height="25" rx="10" fill="#F1F5F9" />
      <rect x="50" y="48" width="100" height="7" fill="#F1F5F9" />
      <rect x="65" y="70" width="70" height="5" rx="2.5" fill="#E2E8F0" />
      <rect x="65" y="82" width="50" height="5" rx="2.5" fill="#E2E8F0" />
      <rect x="65" y="94" width="60" height="5" rx="2.5" fill="#E2E8F0" />
      <circle cx="100" cy="120" r="10" fill="#3B82F6" opacity="0.1" />
      <path d="M95 120L99 124L105 116" stroke="#3B82F6" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="30" cy="50" r="4" fill="#3B82F6" opacity="0.15" />
      <circle cx="170" cy="100" r="3" fill="#3B82F6" opacity="0.2" />
    </svg>
  );
}
