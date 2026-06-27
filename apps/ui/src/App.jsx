import { useEffect, useMemo, useState } from "react";
import { AppLayout } from "./layout/AppLayout.jsx";
import { AIMemoryPage } from "./pages/AIMemoryPage.jsx";
import { LoginPage, SignupPage } from "./pages/AuthPages.jsx";
import { DashboardPage } from "./pages/DashboardPage.jsx";
import { DocumentIntelligencePage } from "./pages/DocumentIntelligencePage.jsx";
import { FeaturesPage } from "./pages/FeaturesPage.jsx";
import { HomePage } from "./pages/HomePage.jsx";
import { PricingPage } from "./pages/PricingPage.jsx";
import { ReportsPage } from "./pages/ReportsPage.jsx";

function useRoute() {
  const [path, setPath] = useState(window.location.pathname);

  useEffect(() => {
    const onPop = () => setPath(window.location.pathname);
    window.addEventListener("popstate", onPop);
    return () => window.removeEventListener("popstate", onPop);
  }, []);

  function navigate(nextPath) {
    window.history.pushState({}, "", nextPath);
    setPath(nextPath);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  return { path, navigate };
}

export default function App() {
  const { path, navigate } = useRoute();
  const [mobileOpen, setMobileOpen] = useState(false);

  const page = useMemo(() => {
    switch (path) {
      case "/features":
        return <FeaturesPage navigate={navigate} />;
      case "/ai-memory":
        return <AIMemoryPage navigate={navigate} />;
      case "/ocr":
        return <DocumentIntelligencePage navigate={navigate} />;
      case "/reports":
        return <ReportsPage navigate={navigate} />;
      case "/pricing":
        return <PricingPage navigate={navigate} />;
      case "/login":
      case "/signin":
        return <LoginPage navigate={navigate} />;
      case "/signup":
        return <SignupPage navigate={navigate} />;
      case "/dashboard":
        return <DashboardPage navigate={navigate} />;
      default:
        return <HomePage navigate={navigate} />;
    }
  }, [path, navigate]);

  return (
    <AppLayout path={path} navigate={navigate} mobileOpen={mobileOpen} setMobileOpen={setMobileOpen}>
      {page}
    </AppLayout>
  );
}
