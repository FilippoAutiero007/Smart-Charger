import { useAuth, useUser, useSignIn, useSignUp } from '@clerk/clerk-expo';
import { useCallback } from 'react';

export const useAuthManager = () => {
  const { isSignedIn, isLoaded, signOut } = useAuth();
  const { user } = useUser();
  const { signIn, setActive: setSignInActive, isLoaded: isSignInLoaded } = useSignIn();
  const { signUp, setActive: setSignUpActive, isLoaded: isSignUpLoaded } = useSignUp();

  const handleSignOut = useCallback(async () => {
    try {
      await signOut();
    } catch (error) {
      console.error('Error signing out:', error);
    }
  }, [signOut]);

  return {
    isSignedIn,
    isLoaded,
    user,
    signIn,
    setSignInActive,
    isSignInLoaded,
    signUp,
    setSignUpActive,
    isSignUpLoaded,
    handleSignOut,
  };
};
