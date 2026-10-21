import { ArchitectureSection } from '@/components/public/landing/ArchitectureSection';
import { AndroidSection } from '@/components/public/landing/AndroidSection';
import { ClosingSection } from '@/components/public/landing/ClosingSection';
import { ContributeSection } from '@/components/public/landing/ContributeSection';
import { DemoSection } from '@/components/public/landing/DemoSection';
import { FailureSection } from '@/components/public/landing/FailureSection';
import { HeroSection } from '@/components/public/landing/HeroSection';
import { MechanismSection } from '@/components/public/landing/MechanismSection';
import { StorySection } from '@/components/public/landing/StorySection';
import { usePageMeta } from '@/lib/seo';

export function LandingPage() {
  usePageMeta({
    title: 'ResQNet — when the signal disappears, the message keeps moving',
    description:
      'ResQNet is an open-source, local-first emergency messaging prototype. Nearby Android phones sign, store and relay SOS messages without a tower, server or account.',
    path: '/',
  });

  return (
    <>
      <HeroSection />
      <StorySection />
      <MechanismSection />
      <DemoSection />
      <FailureSection />
      <AndroidSection />
      <ContributeSection />
      <ArchitectureSection />
      <ClosingSection />
    </>
  );
}
