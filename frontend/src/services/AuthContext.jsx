import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";
import { api } from "./api.js";
import { hasPermission } from "./permissions.js";
import { signOutAndClearUser } from "./interactionActions.js";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    api.me()
      .then((currentUser) => {
        if (active) setUser(currentUser);
      })
      .catch(() => {
        if (active) setUser(null);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, []);

  const signIn = useCallback(async (email, password) => {
    const authenticated = await api.login(email, password);
    setUser(authenticated);
    return authenticated;
  }, []);

  const signUp = useCallback(async (fullName, email, password, confirmPassword, accountType) => {
    const result = await api.register(fullName, email, password, confirmPassword, accountType);
    if (result.user) setUser(result.user);
    return result;
  }, []);

  const signOut = useCallback(async () => {
    await signOutAndClearUser(api.logout, () => setUser(null));
  }, []);

  const value = useMemo(() => ({
    user,
    loading,
    signIn,
    signUp,
    signOut,
    can: (permission) => hasPermission(user, permission),
  }), [user, loading, signIn, signUp, signOut]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used within AuthProvider");
  return context;
}
