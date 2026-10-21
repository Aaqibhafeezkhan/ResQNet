import { ArchitectureSection } from '@/components/public/landing/ArchitectureSection';
import { AndroidSection } from '@/components/public/landing/AndroidSection';
import { ClosingSection } from '@/components/public/landing/ClosingSection';
import { ContributeSection } from '@/components/public/landing/ContributeSection';
import { DemoSection } from '@/components/public/landing/DemoSection';
import { FailureSection } from '@/components/public/landing/FailureSection';
import { HeroSection } from '@/components/public/landing/HeroSection';
import { MechanismSection } from '@/components/public/landing/MechanismSection';
import { StorySection } from '@/components/public/landing/StorySection';
import { SectionTransition } from '@/components/primitives/SectionTransition';
import { usePageMeta } from '@/lib/seo';

export function LandingPage() {
  usePageMeta({
    title: 'ResQNet — when the signal disappears, the message keeps moving',
    description:
      'ResQNet is an open-source, local-first emergency messaging prototype. Nearby Android phones sign, store and relay SOS messages without a tower, server or account.',
    path: '/',
  });

  /*
    The sections are chapters of one argument, not independent blocks, so
    each one arrives with a small directional push. The direction alternates
    with the layout: the two-column sections enter from the side their
    content sits on, so the reader's eye is carried across the page.
  */
  return (
    <>
      <HeroSection />
      <SectionTransition from="bottom">
        <StorySection />
      </SectionTransition>
      <SectionTransition from="left">
        <MechanismSection />
      </SectionTransition>
      <SectionTransition from="right">
        <DemoSection />
      </SectionTransition>
      <SectionTransition from="left">
        <FailureSection />
      </SectionTransition>
      <SectionTransition from="right">
        <AndroidSection />
      </SectionTransition>
      <SectionTransition from="bottom">
        <ContributeSection />
      </SectionTransition>
      <SectionTransition from="right">
        <ArchitectureSection />
      </SectionTransition>
      <SectionTransition from="bottom">
        <ClosingSection />
      </SectionTransition>
    </>
  );
}
