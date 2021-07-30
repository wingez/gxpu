import itertools as it

m = 24 * 2

low_bound = 18.85 - 0.5
high_bound = 18.94 - 0.5

result = []

for nums in it.combinations_with_replacement(range(0, m + 1), 5):

    grades = [nums[0] - 0, nums[1] - nums[0], nums[2] - nums[1], nums[3] - nums[2], nums[4] - nums[3], m - nums[4]]

    grade = sum((grades[0] * 0, grades[1] * 10, grades[2] * 12.5, grades[3] * 15,
                 grades[4] * 17.5, grades[5] * 20))
    grade = grade / m

    if low_bound <= grade <= high_bound:
        result.append((grade, grades))

result.sort(key=lambda x: x[0])

for i, (tot, grades) in enumerate(result):
    print(
        f'{i:4d}: {tot:.7f}\tF:{grades[0] / 2:4.1f}, E:{grades[1] / 2:4.1f}, D:{grades[2] / 2:4.1f}, C:{grades[3] / 2:4.1f}, B:{grades[4] / 2:4.1f}, A:{grades[5] / 2:4.1f}')

print(len(set(i[0] for i in result)))
