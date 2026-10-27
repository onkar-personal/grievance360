import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import type { ReactNode } from "react";

const post = vi.fn();
const get = vi.fn();

vi.mock("@/lib/api", async (orig) => {
  const actual = await orig<typeof import("@/lib/api")>();
  return { ...actual, api: { post: (...a: unknown[]) => post(...a), get: (...a: unknown[]) => get(...a), interceptors: actual.api.interceptors } };
});

import { AuthProvider } from "@/lib/auth";
import { LoginPage, RegisterPage } from "@/pages/public/Auth";
import SubmitComplaint from "@/pages/citizen/SubmitComplaint";
import { RequireRole } from "@/components/AppShell";
import { complaintSchema, registerSchema, validateFile } from "@/lib/schemas";

function wrap(ui: ReactNode, path = "/") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/" element={ui} />
          <Route path="/app/citizen" element={<div>citizen home</div>} />
          <Route path="/app/admin" element={<div>admin home</div>} />
          <Route path="/app/complaints/:id" element={<div>detail page</div>} />
          <Route path="/login" element={<div>login page</div>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

const citizen = { id: 1, fullName: "Asha Rao", email: "asha@example.com", role: "CITIZEN", active: true, createdAt: "2026-01-01T00:00:00Z" };

beforeEach(() => {
  post.mockReset();
  get.mockReset();
});

describe("schemas", () => {
  it("rejects weak passwords and mismatched confirmation", () => {
    const r = registerSchema.safeParse({ fullName: "A B", email: "a@b.co", password: "letters", confirmPassword: "other" });
    expect(r.success).toBe(false);
    const msgs = r.success ? [] : r.error.issues.map((i) => i.message);
    expect(msgs).toContain("Include at least one number");
    expect(msgs).toContain("Passwords do not match");
  });

  it("validates complaint fields", () => {
    expect(complaintSchema.safeParse({ categoryId: 1, title: "Pothole on main road", description: "x".repeat(25), locationPincode: "411001" }).success).toBe(true);
    expect(complaintSchema.safeParse({ categoryId: 1, title: "Pot", description: "short", locationPincode: "12" }).success).toBe(false);
  });

  it("rejects disallowed or oversized files", () => {
    expect(validateFile(new File(["x"], "a.exe", { type: "application/x-msdownload" }))).toMatch(/only JPEG/);
    const big = new File([new Uint8Array(5 * 1024 * 1024 + 1)], "big.png", { type: "image/png" });
    expect(validateFile(big)).toMatch(/larger than 5 MB/);
    expect(validateFile(new File(["x"], "ok.png", { type: "image/png" }))).toBeNull();
  });
});

describe("LoginPage", () => {
  it("shows validation errors without calling the API", async () => {
    wrap(<LoginPage />);
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("Email is required")).toBeInTheDocument();
    expect(screen.getByText("Password is required")).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
  });

  it("shows the server message on bad credentials", async () => {
    post.mockRejectedValueOnce(
      Object.assign(new Error("401"), {
        isAxiosError: true,
        response: { status: 401, data: { status: 401, code: "INVALID_CREDENTIALS", message: "Invalid email or password" } },
      }),
    );
    wrap(<LoginPage />);
    await userEvent.type(screen.getByLabelText("Email"), "asha@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "wrong-pass1");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid email or password");
  });

  it("stores the token and redirects to the role home on success", async () => {
    post.mockResolvedValueOnce({ data: { accessToken: "tok", tokenType: "Bearer", expiresAt: "", user: citizen } });
    wrap(<LoginPage />);
    await userEvent.type(screen.getByLabelText("Email"), "asha@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "Secure123");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(await screen.findByText("citizen home")).toBeInTheDocument();
    expect(post).toHaveBeenCalledWith("/auth/login", { email: "asha@example.com", password: "Secure123" });
    expect(sessionStorage.getItem("g360.token")).toBe("tok");
  });
});

describe("RegisterPage", () => {
  it("never sends a role and maps duplicate email to the field", async () => {
    post.mockRejectedValueOnce(
      Object.assign(new Error("409"), {
        isAxiosError: true,
        response: { status: 409, data: { status: 409, code: "EMAIL_IN_USE", message: "An account with this email already exists" } },
      }),
    );
    wrap(<RegisterPage />);
    await userEvent.type(screen.getByLabelText("Full name"), "Asha Rao");
    await userEvent.type(screen.getByLabelText("Email"), "asha@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "Secure123");
    await userEvent.type(screen.getByLabelText("Confirm password"), "Secure123");
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByText("An account with this email already exists")).toBeInTheDocument();
    const body = post.mock.calls[0][1] as Record<string, unknown>;
    expect(body).not.toHaveProperty("role");
    expect(body).not.toHaveProperty("confirmPassword");
  });
});

describe("SubmitComplaint", () => {
  const categories = [
    { id: 7, name: "Potholes", departmentId: 2, departmentName: "Roads & Public Works", slaHours: 120, defaultPriority: "MEDIUM", active: true },
  ];

  it("validates required fields, then submits and navigates to the new complaint", async () => {
    get.mockImplementation((url: string) => {
      if (url === "/categories") return Promise.resolve({ data: categories });
      return Promise.reject(new Error("unexpected " + url));
    });
    post.mockResolvedValueOnce({ data: { id: 99, reference: "G360-2026-000099" } });
    wrap(<SubmitComplaint />);

    await screen.findByRole("option", { name: "Potholes" });
    await userEvent.click(screen.getByRole("button", { name: "Submit grievance" }));
    expect(await screen.findByText("Select a category", { selector: "p" })).toBeInTheDocument();
    expect(screen.getByText("Title must be at least 5 characters")).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();

    await userEvent.selectOptions(screen.getByLabelText("Category"), "7");
    expect(screen.getByText("Roads & Public Works")).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText("Title"), "Large pothole near school");
    await userEvent.type(screen.getByLabelText("Description"), "There is a deep pothole that has caused two accidents this week.");
    await userEvent.click(screen.getByRole("button", { name: "Submit grievance" }));

    await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    expect(post.mock.calls[0][0]).toBe("/complaints");
    expect(post.mock.calls[0][1]).toMatchObject({ categoryId: 7, title: "Large pothole near school" });
    expect(await screen.findByText("detail page")).toBeInTheDocument();
  });
});

describe("RequireRole", () => {
  it("redirects users without the role to their own home", async () => {
    sessionStorage.setItem("g360.token", "tok");
    get.mockResolvedValueOnce({ data: citizen });
    wrap(
      <RequireRole roles={["ADMIN"]}>
        <div>secret admin page</div>
      </RequireRole>,
    );
    expect(await screen.findByText("citizen home")).toBeInTheDocument();
    expect(screen.queryByText("secret admin page")).not.toBeInTheDocument();
  });
});
