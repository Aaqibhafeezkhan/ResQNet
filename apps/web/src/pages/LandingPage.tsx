import { Capabilities } from '@/components/landing/Capabilities';
import { Architecture } from '@/components/landing/Architecture';
import { DemoCta } from '@/components/landing/DemoCta';
import { Hero } from '@/components/landing/Hero';
import { HowItWorks } from '@/components/landing/HowItWorks';
import { MessageJourney } from '@/components/landing/MessageJourney';
import { Resilience } from '@/components/landing/Resilience';
import { RunLocally } from '@/components/landing/RunLocally';
import { Security } from '@/components/landing/Security';
import { Footer } from '@/components/Footer';
import { SiteHeader } from '@/components/SiteHeader';

/**
 * Public site.
 *
 * A single scrolling document rather than a routed marketing site: the
 * argument is sequential, and a page that a responder has to navigate to find
 * the part they are looking for is the wrong shape for this content.
 */
export function LandingPage() {
  return (
    <div className="min-h-screen bg-base-900">
      <SiteHeader />
      <main id="main">
        <Hero />
        <div id="how">
          <MessageJourney />
          <HowItWorks />
        </div>
        <Resilience />
        <Capabilities />
        <Architecture />
        <Security />
        <DemoCta />
        <RunLocally />
      </main>
      <Footer />
    </div>
  );
}
